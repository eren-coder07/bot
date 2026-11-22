package com.jcraft.jsch;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Receive buffer for a JSch channel with real per-channel SSH flow control.
 *
 * <p>JSch delivers incoming data for every channel from its single session thread and re-opens
 * the SSH window as soon as the data is handed over. With the default piped streams one slow
 * reader (for example a paused video) blocks the session thread and freezes every other
 * connection. This buffer never blocks the session thread. When it holds more than {@code high}
 * bytes it stops granting window, so the server pauses only this channel, and it grants the
 * window again once the reader has drained it below {@code low}.
 *
 * <p>Lives in the com.jcraft.jsch package to reach the channel's window fields.
 */
public final class FlowControlledInput extends InputStream {
  private final Channel channel;
  private final int window;
  private final int high;
  private final int low;
  private final ArrayDeque<byte[]> chunks = new ArrayDeque<>();
  private byte[] head;
  private int headPos;
  private int buffered;
  private boolean eof;
  private boolean closed;
  private boolean paused;

  private FlowControlledInput(Channel channel, int window, int high, int low) {
    this.channel = channel;
    this.window = window;
    this.high = high;
    this.low = low;
  }

  /** Installs the buffer as the channel's receiver. Must be called before {@code connect()}. */
  public static FlowControlledInput attach(Channel channel, int window, int packetSize, int high, int low) {
    channel.setLocalWindowSizeMax(window);
    channel.setLocalWindowSize(window);
    channel.setLocalPacketSize(packetSize);
    FlowControlledInput in = new FlowControlledInput(channel, window, high, low);
    channel.setOutputStream(in.sink, false);
    return in;
  }

  private final OutputStream sink = new OutputStream() {
    @Override
    public void write(int b) {
      write(new byte[] {(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) {
      if (len <= 0) return;
      byte[] copy = Arrays.copyOfRange(b, off, off + len);
      synchronized (FlowControlledInput.this) {
        if (closed) return;
        chunks.addLast(copy);
        buffered += len;
        if (!paused && buffered >= high) {
          // Session.run() only sends WINDOW_ADJUST while lwsize < lwsize_max / 2.
          paused = true;
          channel.lwsize_max = 0;
        }
        FlowControlledInput.this.notifyAll();
      }
    }

    @Override
    public void close() {
      synchronized (FlowControlledInput.this) {
        eof = true;
        FlowControlledInput.this.notifyAll();
      }
    }
  };

  @Override
  public int read() throws IOException {
    byte[] one = new byte[1];
    int n = read(one, 0, 1);
    return n < 0 ? -1 : one[0] & 0xff;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (len == 0) return 0;
    int n;
    boolean resume = false;
    synchronized (this) {
      while (head == null && chunks.isEmpty()) {
        if (eof || closed) return -1;
        if (channel.isClosed()) return -1;
        try {
          wait(1000);
        } catch (InterruptedException e) {
          throw new InterruptedIOException();
        }
      }
      if (head == null) {
        head = chunks.pollFirst();
        headPos = 0;
      }
      n = Math.min(len, head.length - headPos);
      System.arraycopy(head, headPos, b, off, n);
      headPos += n;
      buffered -= n;
      if (headPos >= head.length) head = null;
      if (paused && buffered <= low) {
        paused = false;
        resume = true;
      }
    }
    if (resume) grantWindow();
    return n;
  }

  @Override
  public synchronized int available() {
    return buffered;
  }

  @Override
  public void close() {
    boolean wasPaused;
    synchronized (this) {
      closed = true;
      chunks.clear();
      head = null;
      buffered = 0;
      wasPaused = paused;
      paused = false;
      notifyAll();
    }
    if (wasPaused) grantWindow();
  }

  private void grantWindow() {
    channel.lwsize_max = window;
    int amount = window - channel.lwsize;
    int recipient = channel.getRecipient();
    if (amount <= 0 || recipient == -1) return;
    try {
      Buffer buf = new Buffer(100);
      Packet packet = new Packet(buf);
      packet.reset();
      buf.putByte((byte) Session.SSH_MSG_CHANNEL_WINDOW_ADJUST);
      buf.putInt(recipient);
      buf.putInt(amount);
      synchronized (channel) {
        if (!channel.close) channel.getSession().write(packet);
      }
      channel.setLocalWindowSize(window);
    } catch (Exception ignored) {
      // channel or session is going away
    }
  }
}

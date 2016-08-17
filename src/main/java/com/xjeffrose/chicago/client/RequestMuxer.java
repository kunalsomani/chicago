package com.xjeffrose.chicago.client;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.internal.PlatformDependent;
import java.net.InetSocketAddress;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RequestMuxer<T> {
  private static final int HIGH_WATER_MARK = 1618 * 3;
  private static final int POOL_SIZE = 4;

  private final String addr;
  private final EventLoopGroup workerLoop;
  private final AtomicBoolean isRunning = new AtomicBoolean();
  private final Deque<ChannelFuture> connectionQ = PlatformDependent.newConcurrentDeque();
  @Getter
  private final Deque<MuxedMessage<T>> messageQ = PlatformDependent.newConcurrentDeque();
  @Setter
  private ChicagoConnector connector;
  private AtomicLong counter = new AtomicLong();
  private AtomicBoolean connectionRebuild = new AtomicBoolean(false);

  public RequestMuxer(String addr, ChannelHandler handler, EventLoopGroup workerLoop) {
    this.addr = addr;
    this.workerLoop = workerLoop;
    this.connector = new ChicagoConnector(handler, workerLoop);
  }

  public void start() throws Exception {
    buildInitialConnectionQ();
    blockAndAwaitPool();
    isRunning.set(true);

    workerLoop.scheduleAtFixedRate(() -> {
      if(messageQ.size() > 0){
        drainMessageQ();
      }
    },0,12,TimeUnit.MILLISECONDS);

    workerLoop.scheduleAtFixedRate(() -> {
      if(connectionRebuild.get()){
        rebuildConnectionQ();
      }
    },0,10,TimeUnit.MILLISECONDS);
  }

  public void shutdownGracefully() {
    isRunning.set(false);
  }

  private InetSocketAddress address(String node) {
    String chunks[] = node.split(":");
    return new InetSocketAddress(chunks[0], Integer.parseInt(chunks[1]));
  }

  private void buildInitialConnectionQ() {
    for (int i = 0; i < POOL_SIZE; i++) {
      Futures.addCallback(connector.connect(address(addr)), new FutureCallback<ChannelFuture>() {
        @Override
        public void onSuccess(@Nullable ChannelFuture channelFuture) {
          connectionQ.addLast(channelFuture);

        }

        @Override
        public void onFailure(Throwable throwable) {
          log.error("Error connecting to " + addr, throwable);
        }
      });
    }
  }

  void rebuildConnectionQ() {
    rebuildConnectionQ(this.connectionQ);
  }

  private void rebuildConnectionQ(Deque<ChannelFuture> connectionQ) {
    connectionQ.stream().parallel().forEach(xs -> {
      ChannelFuture cf = xs;
//      connectionQ.remove(xs);
      if (cf.channel().isActive()) {
//        connectionQ.addLast(cf);
      } else {
        connectionQ.remove(xs);
        Futures.addCallback(connector.connect(address(addr)), new FutureCallback<ChannelFuture>() {
          @Override
          public void onSuccess(@Nullable ChannelFuture channelFuture) {
            connectionQ.addLast(channelFuture);
          }

          @Override
          public void onFailure(Throwable throwable) {
            log.error("Error connecting to " + addr, throwable);
          }
        });
      }
    });
  }

  private boolean blockAndAwaitPool(long timeout, TimeUnit timeUnit) {

    return blockAndAwaitPool();
  }

  private boolean blockAndAwaitPool() {
    while (connectionQ.size() != POOL_SIZE) {
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        e.printStackTrace();
        return false;
      }
    }

    return true;
  }

  public void write(T sendReq, SettableFuture<Boolean> f) {
    if (isRunning.get()) {
      if (counter.incrementAndGet() > HIGH_WATER_MARK) {
        messageQ.addLast(new MuxedMessage<>(sendReq, f));
      } else {
        drainMessageQ(sendReq, f);
//      if (messageQ.size() > HIGH_WATER_MARK) {
//        drainMessageQ();
//      }
      }
    }
  }

  private Channel requestNode(){

    ChannelFuture cf = connectionQ.removeFirst();

//    ChannelFuture cf = connectionQ.peek();
    if ((cf != null) && cf.isSuccess()) {
      if (cf.channel().isActive()) {
        connectionQ.addLast(cf);
        return cf.channel();
      } else {

//        while(!cf.channel().isWritable()){
//          try {
//            Thread.sleep(1);
//          } catch (InterruptedException e) {
//            e.printStackTrace();
//          }
//        }
//        connectionQ.addLast(cf);
        connectionRebuild.set(true);
        return connectionQ.peekLast().channel();
      }
    } else {
      log.info("Rebuilding connectionQ when channel is not successful!!!");
      connectionRebuild.set(true);
//      return requestNode();
      return connectionQ.peekLast().channel();
    }
  }

//  private Channel requestNode(){
//
//    ChannelFuture cf = connectionQ.pollFirst();
//    if ((cf != null) && cf.isSuccess()) {
//      if (cf.channel().isWritable()) {
//        connectionQ.addLast(cf);
//        return cf.channel();
//      } else {
//
//        while(!cf.channel().isWritable()){
//          try {
//            Thread.sleep(1);
//          } catch (InterruptedException e) {
//            e.printStackTrace();
//          }
//        }
//        connectionQ.addLast(cf);
//        return cf.channel();
//      }
//    } else {
//      log.info("Rebuilding connectionQ when channel is not successful!!!");
//      connectionRebuild.set(true);
//      return requestNode();
//    }
//  }

  private void drainMessageQ() {
    Channel ch = requestNode();
    while (isRunning.get() && messageQ.size() > 0) {
      final MuxedMessage<T> mm = messageQ.pollFirst();
//      counter.incrementAndGet();
      ch.write(mm.getMsg()).addListener(new GenericFutureListener<Future<? super Void>>() {
        @Override
        public void operationComplete(Future<? super Void> future) throws Exception {
          if (future.isSuccess()) {
            mm.getF().set(true);
          } else {
            mm.getF().set(false);
            mm.getF().setException(future.cause());
          }
        }
      });
    }
    ch.flush();
  }

  private void drainMessageQ(T sendReq, SettableFuture<Boolean> f) {
    if (isRunning.get()) {
      requestNode().writeAndFlush(sendReq).addListener(new GenericFutureListener<Future<? super Void>>() {
        @Override
        public void operationComplete(Future<? super Void> future) throws Exception {
          if (future.isSuccess()) {
            f.set(true);
            counter.decrementAndGet();
          } else {
            f.set(false);
            f.setException(future.cause());
          }
        }
      });
    }
  }

  @Data
  private class MuxedMessage<T> {
    private final T msg;
    private final SettableFuture<Boolean> f;
  }

}

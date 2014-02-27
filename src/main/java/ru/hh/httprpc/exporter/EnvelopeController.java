package ru.hh.httprpc.exporter;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import ru.hh.httprpc.Envelope;
import ru.hh.httprpc.util.concurrent.FutureListener;

public class EnvelopeController implements RpcController {

  private boolean failed = false;
  private Envelope envelope;
  private Throwable reason;

  public EnvelopeController() {
  }

  public EnvelopeController(Envelope envelope) {
    this.envelope = envelope;
  }

  @Override
  public void reset() {
    failed = false;
    envelope = null;
    reason = null;
  }

  @Override
  public boolean failed() {
    return failed;
  }

  @Override
  public String errorText() {
    return failed ? reason.getMessage() : null;
  }

  @Override
  public void startCancel() {
  }

  @Override
  public void setFailed(String reason) {
    setFailed(new Throwable(reason));
  }

  public void setFailed(Throwable reason) {
    failed = true;
    this.reason = reason;
  }

  @Override
  public boolean isCanceled() {
    return false;
  }

  @Override
  public void notifyOnCancel(RpcCallback<Object> callback) {
  }

  public Envelope getEnvelope() {
    return envelope;
  }

  public void setEnvelope(Envelope envelope) {
    this.envelope = envelope;
  }

  public Throwable getReason() {
    return failed ? reason : null;
  }

  public <T> void listenToFuture(ListenableFuture<T> future, final RpcCallback<T> done) {
    new FutureListener<T>(future) {
      protected void success(T result) {
        done.run(result);
      }

      protected void exception(Throwable t) {
        setFailed(t);
        done.run(null);
      }

      protected void cancelled() {
        setFailed("Request was cancelled by client");
        done.run(null);
      }

      protected void interrupted(InterruptedException e) {
        super.interrupted(e);
        setFailed(e);
        done.run(null);
      }
    };
  }
}


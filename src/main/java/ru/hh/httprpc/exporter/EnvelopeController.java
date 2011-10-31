package ru.hh.httprpc.exporter;

import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import ru.hh.httprpc.Envelope;

public class EnvelopeController implements RpcController {

  private boolean failed = false;
  private Envelope envelope;
  private Throwable reason;

  public EnvelopeController() { }

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
}


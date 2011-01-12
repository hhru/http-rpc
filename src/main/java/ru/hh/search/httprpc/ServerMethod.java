package ru.hh.search.httprpc;

public interface ServerMethod<O, I> {
  Class<I> getInputClass();
  O call(Envelope envelope, I argument);
}

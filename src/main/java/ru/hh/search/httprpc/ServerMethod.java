package ru.hh.search.httprpc;

public interface ServerMethod<O, I> {
  O call(Envelope envelope, I argument);
}

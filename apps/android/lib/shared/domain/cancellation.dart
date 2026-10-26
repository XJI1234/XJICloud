/// Framework-free, cooperative cancellation; adapters attach transport aborts.
final class Cancellation {
  bool _cancelled = false;
  final _listeners = <void Function()>[];
  bool get isCancelled => _cancelled;
  void Function() listen(void Function() listener) {
    if (_cancelled) {
      listener();
      return () {};
    }
    _listeners.add(listener);
    return () => _listeners.remove(listener);
  }

  void cancel() {
    if (_cancelled) return;
    _cancelled = true;
    for (final listener in List.of(_listeners)) {
      listener();
    }
    _listeners.clear();
  }
}

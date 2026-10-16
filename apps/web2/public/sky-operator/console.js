"use strict";
(() => {
  var __create = Object.create;
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __getProtoOf = Object.getPrototypeOf;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __commonJS = (cb, mod) => function __require() {
    return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;
  };
  var __copyProps = (to, from, except, desc) => {
    if (from && typeof from === "object" || typeof from === "function") {
      for (let key of __getOwnPropNames(from))
        if (!__hasOwnProp.call(to, key) && key !== except)
          __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
    }
    return to;
  };
  var __toESM = (mod, isNodeMode, target) => (target = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(
    // If the importer is in node compatibility mode or this is not an ESM
    // file that has been converted to a CommonJS file using a Babel-
    // compatible transform (i.e. "__esModule" has not been set), then set
    // "default" to the CommonJS "module.exports" for node compatibility.
    isNodeMode || !mod || !mod.__esModule ? __defProp(target, "default", { value: mod, enumerable: true }) : target,
    mod
  ));

  // node_modules/mpegts.js/dist/mpegts.js
  var require_mpegts = __commonJS({
    "node_modules/mpegts.js/dist/mpegts.js"(exports, module) {
      !(function(e, t) {
        "object" == typeof exports && "object" == typeof module ? module.exports = t() : "function" == typeof define && define.amd ? define([], t) : "object" == typeof exports ? exports.mpegts = t() : e.mpegts = t();
      })(exports, function() {
        return (function() {
          var e = { 964: function(e2, t2, i2) {
            e2.exports = (function() {
              "use strict";
              function e3(e4) {
                return "function" == typeof e4;
              }
              var t3 = Array.isArray ? Array.isArray : function(e4) {
                return "[object Array]" === Object.prototype.toString.call(e4);
              }, a = 0, n = void 0, r = void 0, o = function(e4, t4) {
                h[a] = e4, h[a + 1] = t4, 2 === (a += 2) && (r ? r(f) : y());
              };
              var s = "undefined" != typeof window ? window : void 0, d = s || {}, _ = d.MutationObserver || d.WebKitMutationObserver, l = "undefined" == typeof self && "undefined" != typeof process && "[object process]" === {}.toString.call(process), u = "undefined" != typeof Uint8ClampedArray && "undefined" != typeof importScripts && "undefined" != typeof MessageChannel;
              function c() {
                var e4 = setTimeout;
                return function() {
                  return e4(f, 1);
                };
              }
              var h = new Array(1e3);
              function f() {
                for (var e4 = 0; e4 < a; e4 += 2) (0, h[e4])(h[e4 + 1]), h[e4] = void 0, h[e4 + 1] = void 0;
                a = 0;
              }
              var p, m, g, v, y = void 0;
              function S(e4, t4) {
                var i3 = this, a2 = new this.constructor(b);
                void 0 === a2[A] && x(a2);
                var n2 = i3._state;
                if (n2) {
                  var r2 = arguments[n2 - 1];
                  o(function() {
                    return O(n2, a2, r2, i3._result);
                  });
                } else I(i3, a2, e4, t4);
                return a2;
              }
              function E(e4) {
                if (e4 && "object" == typeof e4 && e4.constructor === this) return e4;
                var t4 = new this(b);
                return D(t4, e4), t4;
              }
              y = l ? function() {
                return process.nextTick(f);
              } : _ ? (m = 0, g = new _(f), v = document.createTextNode(""), g.observe(v, { characterData: true }), function() {
                v.data = m = ++m % 2;
              }) : u ? ((p = new MessageChannel()).port1.onmessage = f, function() {
                return p.port2.postMessage(0);
              }) : void 0 === s ? (function() {
                try {
                  var e4 = Function("return this")().require("vertx");
                  return void 0 !== (n = e4.runOnLoop || e4.runOnContext) ? function() {
                    n(f);
                  } : c();
                } catch (e5) {
                  return c();
                }
              })() : c();
              var A = Math.random().toString(36).substring(2);
              function b() {
              }
              var R = void 0, T = 1, L = 2;
              function k(t4, i3, a2) {
                i3.constructor === t4.constructor && a2 === S && i3.constructor.resolve === E ? (function(e4, t5) {
                  t5._state === T ? C(e4, t5._result) : t5._state === L ? w(e4, t5._result) : I(t5, void 0, function(t6) {
                    return D(e4, t6);
                  }, function(t6) {
                    return w(e4, t6);
                  });
                })(t4, i3) : void 0 === a2 ? C(t4, i3) : e3(a2) ? (function(e4, t5, i4) {
                  o(function(e5) {
                    var a3 = false, n2 = (function(e6, t6, i5, a4) {
                      try {
                        e6.call(t6, i5, a4);
                      } catch (e7) {
                        return e7;
                      }
                    })(i4, t5, function(i5) {
                      a3 || (a3 = true, t5 !== i5 ? D(e5, i5) : C(e5, i5));
                    }, function(t6) {
                      a3 || (a3 = true, w(e5, t6));
                    }, e5._label);
                    !a3 && n2 && (a3 = true, w(e5, n2));
                  }, e4);
                })(t4, i3, a2) : C(t4, i3);
              }
              function D(e4, t4) {
                if (e4 === t4) w(e4, new TypeError("You cannot resolve a promise with itself"));
                else if (n2 = typeof (a2 = t4), null === a2 || "object" !== n2 && "function" !== n2) C(e4, t4);
                else {
                  var i3 = void 0;
                  try {
                    i3 = t4.then;
                  } catch (t5) {
                    return void w(e4, t5);
                  }
                  k(e4, t4, i3);
                }
                var a2, n2;
              }
              function M(e4) {
                e4._onerror && e4._onerror(e4._result), B(e4);
              }
              function C(e4, t4) {
                e4._state === R && (e4._result = t4, e4._state = T, 0 !== e4._subscribers.length && o(B, e4));
              }
              function w(e4, t4) {
                e4._state === R && (e4._state = L, e4._result = t4, o(M, e4));
              }
              function I(e4, t4, i3, a2) {
                var n2 = e4._subscribers, r2 = n2.length;
                e4._onerror = null, n2[r2] = t4, n2[r2 + T] = i3, n2[r2 + L] = a2, 0 === r2 && e4._state && o(B, e4);
              }
              function B(e4) {
                var t4 = e4._subscribers, i3 = e4._state;
                if (0 !== t4.length) {
                  for (var a2 = void 0, n2 = void 0, r2 = e4._result, o2 = 0; o2 < t4.length; o2 += 3) a2 = t4[o2], n2 = t4[o2 + i3], a2 ? O(i3, a2, n2, r2) : n2(r2);
                  e4._subscribers.length = 0;
                }
              }
              function O(t4, i3, a2, n2) {
                var r2 = e3(a2), o2 = void 0, s2 = void 0, d2 = true;
                if (r2) {
                  try {
                    o2 = a2(n2);
                  } catch (e4) {
                    d2 = false, s2 = e4;
                  }
                  if (i3 === o2) return void w(i3, new TypeError("A promises callback cannot return that same promise."));
                } else o2 = n2;
                i3._state !== R || (r2 && d2 ? D(i3, o2) : false === d2 ? w(i3, s2) : t4 === T ? C(i3, o2) : t4 === L && w(i3, o2));
              }
              var P = 0;
              function x(e4) {
                e4[A] = P++, e4._state = void 0, e4._result = void 0, e4._subscribers = [];
              }
              var U = (function() {
                function e4(e5, i3) {
                  this._instanceConstructor = e5, this.promise = new e5(b), this.promise[A] || x(this.promise), t3(i3) ? (this.length = i3.length, this._remaining = i3.length, this._result = new Array(this.length), 0 === this.length ? C(this.promise, this._result) : (this.length = this.length || 0, this._enumerate(i3), 0 === this._remaining && C(this.promise, this._result))) : w(this.promise, new Error("Array Methods must be provided an Array"));
                }
                return e4.prototype._enumerate = function(e5) {
                  for (var t4 = 0; this._state === R && t4 < e5.length; t4++) this._eachEntry(e5[t4], t4);
                }, e4.prototype._eachEntry = function(e5, t4) {
                  var i3 = this._instanceConstructor, a2 = i3.resolve;
                  if (a2 === E) {
                    var n2 = void 0, r2 = void 0, o2 = false;
                    try {
                      n2 = e5.then;
                    } catch (e6) {
                      o2 = true, r2 = e6;
                    }
                    if (n2 === S && e5._state !== R) this._settledAt(e5._state, t4, e5._result);
                    else if ("function" != typeof n2) this._remaining--, this._result[t4] = e5;
                    else if (i3 === N) {
                      var s2 = new i3(b);
                      o2 ? w(s2, r2) : k(s2, e5, n2), this._willSettleAt(s2, t4);
                    } else this._willSettleAt(new i3(function(t5) {
                      return t5(e5);
                    }), t4);
                  } else this._willSettleAt(a2(e5), t4);
                }, e4.prototype._settledAt = function(e5, t4, i3) {
                  var a2 = this.promise;
                  a2._state === R && (this._remaining--, e5 === L ? w(a2, i3) : this._result[t4] = i3), 0 === this._remaining && C(a2, this._result);
                }, e4.prototype._willSettleAt = function(e5, t4) {
                  var i3 = this;
                  I(e5, void 0, function(e6) {
                    return i3._settledAt(T, t4, e6);
                  }, function(e6) {
                    return i3._settledAt(L, t4, e6);
                  });
                }, e4;
              })();
              var N = (function() {
                function t4(e4) {
                  this[A] = P++, this._result = this._state = void 0, this._subscribers = [], b !== e4 && ("function" != typeof e4 && (function() {
                    throw new TypeError("You must pass a resolver function as the first argument to the promise constructor");
                  })(), this instanceof t4 ? (function(e5, t5) {
                    try {
                      t5(function(t6) {
                        D(e5, t6);
                      }, function(t6) {
                        w(e5, t6);
                      });
                    } catch (t6) {
                      w(e5, t6);
                    }
                  })(this, e4) : (function() {
                    throw new TypeError("Failed to construct 'Promise': Please use the 'new' operator, this object constructor cannot be called as a function.");
                  })());
                }
                return t4.prototype.catch = function(e4) {
                  return this.then(null, e4);
                }, t4.prototype.finally = function(t5) {
                  var i3 = this, a2 = i3.constructor;
                  return e3(t5) ? i3.then(function(e4) {
                    return a2.resolve(t5()).then(function() {
                      return e4;
                    });
                  }, function(e4) {
                    return a2.resolve(t5()).then(function() {
                      throw e4;
                    });
                  }) : i3.then(t5, t5);
                }, t4;
              })();
              return N.prototype.then = S, N.all = function(e4) {
                return new U(this, e4).promise;
              }, N.race = function(e4) {
                var i3 = this;
                return t3(e4) ? new i3(function(t4, a2) {
                  for (var n2 = e4.length, r2 = 0; r2 < n2; r2++) i3.resolve(e4[r2]).then(t4, a2);
                }) : new i3(function(e5, t4) {
                  return t4(new TypeError("You must pass an array to race."));
                });
              }, N.resolve = E, N.reject = function(e4) {
                var t4 = new this(b);
                return w(t4, e4), t4;
              }, N._setScheduler = function(e4) {
                r = e4;
              }, N._setAsap = function(e4) {
                o = e4;
              }, N._asap = o, N.polyfill = function() {
                var e4 = void 0;
                if (void 0 !== i2.g) e4 = i2.g;
                else if ("undefined" != typeof self) e4 = self;
                else try {
                  e4 = Function("return this")();
                } catch (e5) {
                  throw new Error("polyfill failed because global object is unavailable in this environment");
                }
                var t4 = e4.Promise;
                if (t4) {
                  var a2 = null;
                  try {
                    a2 = Object.prototype.toString.call(t4.resolve());
                  } catch (e5) {
                  }
                  if ("[object Promise]" === a2 && !t4.cast) return;
                }
                e4.Promise = N;
              }, N.Promise = N, N;
            })();
          }, 7: function(e2) {
            "use strict";
            var t2, i2 = "object" == typeof Reflect ? Reflect : null, a = i2 && "function" == typeof i2.apply ? i2.apply : function(e3, t3, i3) {
              return Function.prototype.apply.call(e3, t3, i3);
            };
            t2 = i2 && "function" == typeof i2.ownKeys ? i2.ownKeys : Object.getOwnPropertySymbols ? function(e3) {
              return Object.getOwnPropertyNames(e3).concat(Object.getOwnPropertySymbols(e3));
            } : function(e3) {
              return Object.getOwnPropertyNames(e3);
            };
            var n = Number.isNaN || function(e3) {
              return e3 != e3;
            };
            function r() {
              r.init.call(this);
            }
            e2.exports = r, e2.exports.once = function(e3, t3) {
              return new Promise(function(i3, a2) {
                function n2(i4) {
                  e3.removeListener(t3, r2), a2(i4);
                }
                function r2() {
                  "function" == typeof e3.removeListener && e3.removeListener("error", n2), i3([].slice.call(arguments));
                }
                p(e3, t3, r2, { once: true }), "error" !== t3 && (function(e4, t4) {
                  "function" == typeof e4.on && p(e4, "error", t4, { once: true });
                })(e3, n2);
              });
            }, r.EventEmitter = r, r.prototype._events = void 0, r.prototype._eventsCount = 0, r.prototype._maxListeners = void 0;
            var o = 10;
            function s(e3) {
              if ("function" != typeof e3) throw new TypeError('The "listener" argument must be of type Function. Received type ' + typeof e3);
            }
            function d(e3) {
              return void 0 === e3._maxListeners ? r.defaultMaxListeners : e3._maxListeners;
            }
            function _(e3, t3, i3, a2) {
              var n2, r2, o2, _2;
              if (s(i3), void 0 === (r2 = e3._events) ? (r2 = e3._events = /* @__PURE__ */ Object.create(null), e3._eventsCount = 0) : (void 0 !== r2.newListener && (e3.emit("newListener", t3, i3.listener ? i3.listener : i3), r2 = e3._events), o2 = r2[t3]), void 0 === o2) o2 = r2[t3] = i3, ++e3._eventsCount;
              else if ("function" == typeof o2 ? o2 = r2[t3] = a2 ? [i3, o2] : [o2, i3] : a2 ? o2.unshift(i3) : o2.push(i3), (n2 = d(e3)) > 0 && o2.length > n2 && !o2.warned) {
                o2.warned = true;
                var l2 = new Error("Possible EventEmitter memory leak detected. " + o2.length + " " + String(t3) + " listeners added. Use emitter.setMaxListeners() to increase limit");
                l2.name = "MaxListenersExceededWarning", l2.emitter = e3, l2.type = t3, l2.count = o2.length, _2 = l2, console && console.warn && console.warn(_2);
              }
              return e3;
            }
            function l() {
              if (!this.fired) return this.target.removeListener(this.type, this.wrapFn), this.fired = true, 0 === arguments.length ? this.listener.call(this.target) : this.listener.apply(this.target, arguments);
            }
            function u(e3, t3, i3) {
              var a2 = { fired: false, wrapFn: void 0, target: e3, type: t3, listener: i3 }, n2 = l.bind(a2);
              return n2.listener = i3, a2.wrapFn = n2, n2;
            }
            function c(e3, t3, i3) {
              var a2 = e3._events;
              if (void 0 === a2) return [];
              var n2 = a2[t3];
              return void 0 === n2 ? [] : "function" == typeof n2 ? i3 ? [n2.listener || n2] : [n2] : i3 ? (function(e4) {
                for (var t4 = new Array(e4.length), i4 = 0; i4 < t4.length; ++i4) t4[i4] = e4[i4].listener || e4[i4];
                return t4;
              })(n2) : f(n2, n2.length);
            }
            function h(e3) {
              var t3 = this._events;
              if (void 0 !== t3) {
                var i3 = t3[e3];
                if ("function" == typeof i3) return 1;
                if (void 0 !== i3) return i3.length;
              }
              return 0;
            }
            function f(e3, t3) {
              for (var i3 = new Array(t3), a2 = 0; a2 < t3; ++a2) i3[a2] = e3[a2];
              return i3;
            }
            function p(e3, t3, i3, a2) {
              if ("function" == typeof e3.on) a2.once ? e3.once(t3, i3) : e3.on(t3, i3);
              else {
                if ("function" != typeof e3.addEventListener) throw new TypeError('The "emitter" argument must be of type EventEmitter. Received type ' + typeof e3);
                e3.addEventListener(t3, function n2(r2) {
                  a2.once && e3.removeEventListener(t3, n2), i3(r2);
                });
              }
            }
            Object.defineProperty(r, "defaultMaxListeners", { enumerable: true, get: function() {
              return o;
            }, set: function(e3) {
              if ("number" != typeof e3 || e3 < 0 || n(e3)) throw new RangeError('The value of "defaultMaxListeners" is out of range. It must be a non-negative number. Received ' + e3 + ".");
              o = e3;
            } }), r.init = function() {
              void 0 !== this._events && this._events !== Object.getPrototypeOf(this)._events || (this._events = /* @__PURE__ */ Object.create(null), this._eventsCount = 0), this._maxListeners = this._maxListeners || void 0;
            }, r.prototype.setMaxListeners = function(e3) {
              if ("number" != typeof e3 || e3 < 0 || n(e3)) throw new RangeError('The value of "n" is out of range. It must be a non-negative number. Received ' + e3 + ".");
              return this._maxListeners = e3, this;
            }, r.prototype.getMaxListeners = function() {
              return d(this);
            }, r.prototype.emit = function(e3) {
              for (var t3 = [], i3 = 1; i3 < arguments.length; i3++) t3.push(arguments[i3]);
              var n2 = "error" === e3, r2 = this._events;
              if (void 0 !== r2) n2 = n2 && void 0 === r2.error;
              else if (!n2) return false;
              if (n2) {
                var o2;
                if (t3.length > 0 && (o2 = t3[0]), o2 instanceof Error) throw o2;
                var s2 = new Error("Unhandled error." + (o2 ? " (" + o2.message + ")" : ""));
                throw s2.context = o2, s2;
              }
              var d2 = r2[e3];
              if (void 0 === d2) return false;
              if ("function" == typeof d2) a(d2, this, t3);
              else {
                var _2 = d2.length, l2 = f(d2, _2);
                for (i3 = 0; i3 < _2; ++i3) a(l2[i3], this, t3);
              }
              return true;
            }, r.prototype.addListener = function(e3, t3) {
              return _(this, e3, t3, false);
            }, r.prototype.on = r.prototype.addListener, r.prototype.prependListener = function(e3, t3) {
              return _(this, e3, t3, true);
            }, r.prototype.once = function(e3, t3) {
              return s(t3), this.on(e3, u(this, e3, t3)), this;
            }, r.prototype.prependOnceListener = function(e3, t3) {
              return s(t3), this.prependListener(e3, u(this, e3, t3)), this;
            }, r.prototype.removeListener = function(e3, t3) {
              var i3, a2, n2, r2, o2;
              if (s(t3), void 0 === (a2 = this._events)) return this;
              if (void 0 === (i3 = a2[e3])) return this;
              if (i3 === t3 || i3.listener === t3) 0 === --this._eventsCount ? this._events = /* @__PURE__ */ Object.create(null) : (delete a2[e3], a2.removeListener && this.emit("removeListener", e3, i3.listener || t3));
              else if ("function" != typeof i3) {
                for (n2 = -1, r2 = i3.length - 1; r2 >= 0; r2--) if (i3[r2] === t3 || i3[r2].listener === t3) {
                  o2 = i3[r2].listener, n2 = r2;
                  break;
                }
                if (n2 < 0) return this;
                0 === n2 ? i3.shift() : (function(e4, t4) {
                  for (; t4 + 1 < e4.length; t4++) e4[t4] = e4[t4 + 1];
                  e4.pop();
                })(i3, n2), 1 === i3.length && (a2[e3] = i3[0]), void 0 !== a2.removeListener && this.emit("removeListener", e3, o2 || t3);
              }
              return this;
            }, r.prototype.off = r.prototype.removeListener, r.prototype.removeAllListeners = function(e3) {
              var t3, i3, a2;
              if (void 0 === (i3 = this._events)) return this;
              if (void 0 === i3.removeListener) return 0 === arguments.length ? (this._events = /* @__PURE__ */ Object.create(null), this._eventsCount = 0) : void 0 !== i3[e3] && (0 === --this._eventsCount ? this._events = /* @__PURE__ */ Object.create(null) : delete i3[e3]), this;
              if (0 === arguments.length) {
                var n2, r2 = Object.keys(i3);
                for (a2 = 0; a2 < r2.length; ++a2) "removeListener" !== (n2 = r2[a2]) && this.removeAllListeners(n2);
                return this.removeAllListeners("removeListener"), this._events = /* @__PURE__ */ Object.create(null), this._eventsCount = 0, this;
              }
              if ("function" == typeof (t3 = i3[e3])) this.removeListener(e3, t3);
              else if (void 0 !== t3) for (a2 = t3.length - 1; a2 >= 0; a2--) this.removeListener(e3, t3[a2]);
              return this;
            }, r.prototype.listeners = function(e3) {
              return c(this, e3, true);
            }, r.prototype.rawListeners = function(e3) {
              return c(this, e3, false);
            }, r.listenerCount = function(e3, t3) {
              return "function" == typeof e3.listenerCount ? e3.listenerCount(t3) : h.call(e3, t3);
            }, r.prototype.listenerCount = h, r.prototype.eventNames = function() {
              return this._eventsCount > 0 ? t2(this._events) : [];
            };
          }, 955: function(e2, t2, i2) {
            "use strict";
            i2.r(t2);
            var a = (function() {
              function e3() {
                this.mimeType = null, this.duration = null, this.hasAudio = null, this.hasVideo = null, this.audioCodec = null, this.videoCodec = null, this.audioDataRate = null, this.videoDataRate = null, this.audioSampleRate = null, this.audioChannelCount = null, this.width = null, this.height = null, this.fps = null, this.profile = null, this.level = null, this.refFrames = null, this.chromaFormat = null, this.sarNum = null, this.sarDen = null, this.metadata = null, this.segments = null, this.segmentCount = null, this.hasKeyframesIndex = null, this.keyframesIndex = null;
              }
              return e3.prototype.isComplete = function() {
                var e4 = false === this.hasAudio || true === this.hasAudio && null != this.audioCodec && null != this.audioSampleRate && null != this.audioChannelCount, t3 = false === this.hasVideo || true === this.hasVideo && null != this.videoCodec && null != this.width && null != this.height && null != this.fps && null != this.profile && null != this.level && null != this.refFrames && null != this.chromaFormat && null != this.sarNum && null != this.sarDen;
                return null != this.mimeType && e4 && t3;
              }, e3.prototype.isSeekable = function() {
                return true === this.hasKeyframesIndex;
              }, e3.prototype.getNearestKeyframe = function(e4) {
                if (null == this.keyframesIndex) return null;
                var t3 = this.keyframesIndex, i3 = this._search(t3.times, e4);
                return { index: i3, milliseconds: t3.times[i3], fileposition: t3.filepositions[i3] };
              }, e3.prototype._search = function(e4, t3) {
                var i3 = 0, a2 = e4.length - 1, n = 0, r = 0, o = a2;
                for (t3 < e4[0] && (i3 = 0, r = o + 1); r <= o; ) {
                  if ((n = r + Math.floor((o - r) / 2)) === a2 || t3 >= e4[n] && t3 < e4[n + 1]) {
                    i3 = n;
                    break;
                  }
                  e4[n] < t3 ? r = n + 1 : o = n - 1;
                }
                return i3;
              }, e3;
            })();
            t2.default = a;
          }, 47: function(e2, t2, i2) {
            "use strict";
            i2.r(t2), i2.d(t2, { IDRSampleList: function() {
              return r;
            }, MediaSegmentInfo: function() {
              return n;
            }, MediaSegmentInfoList: function() {
              return o;
            }, SampleInfo: function() {
              return a;
            } });
            var a = function(e3, t3, i3, a2, n2) {
              this.dts = e3, this.pts = t3, this.duration = i3, this.originalDts = a2, this.isSyncPoint = n2, this.fileposition = null;
            }, n = (function() {
              function e3() {
                this.beginDts = 0, this.endDts = 0, this.beginPts = 0, this.endPts = 0, this.originalBeginDts = 0, this.originalEndDts = 0, this.syncPoints = [], this.firstSample = null, this.lastSample = null;
              }
              return e3.prototype.appendSyncPoint = function(e4) {
                e4.isSyncPoint = true, this.syncPoints.push(e4);
              }, e3;
            })(), r = (function() {
              function e3() {
                this._list = [];
              }
              return e3.prototype.clear = function() {
                this._list = [];
              }, e3.prototype.appendArray = function(e4) {
                var t3 = this._list;
                0 !== e4.length && (t3.length > 0 && e4[0].originalDts < t3[t3.length - 1].originalDts && this.clear(), Array.prototype.push.apply(t3, e4));
              }, e3.prototype.getLastSyncPointBeforeDts = function(e4) {
                if (0 == this._list.length) return null;
                var t3 = this._list, i3 = 0, a2 = t3.length - 1, n2 = 0, r2 = 0, o2 = a2;
                for (e4 < t3[0].dts && (i3 = 0, r2 = o2 + 1); r2 <= o2; ) {
                  if ((n2 = r2 + Math.floor((o2 - r2) / 2)) === a2 || e4 >= t3[n2].dts && e4 < t3[n2 + 1].dts) {
                    i3 = n2;
                    break;
                  }
                  t3[n2].dts < e4 ? r2 = n2 + 1 : o2 = n2 - 1;
                }
                return this._list[i3];
              }, e3;
            })(), o = (function() {
              function e3(e4) {
                this._type = e4, this._list = [], this._lastAppendLocation = -1;
              }
              return Object.defineProperty(e3.prototype, "type", { get: function() {
                return this._type;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "length", { get: function() {
                return this._list.length;
              }, enumerable: false, configurable: true }), e3.prototype.isEmpty = function() {
                return 0 === this._list.length;
              }, e3.prototype.clear = function() {
                this._list = [], this._lastAppendLocation = -1;
              }, e3.prototype._searchNearestSegmentBefore = function(e4) {
                var t3 = this._list;
                if (0 === t3.length) return -2;
                var i3 = t3.length - 1, a2 = 0, n2 = 0, r2 = i3, o2 = 0;
                if (e4 < t3[0].originalBeginDts) return -1;
                for (; n2 <= r2; ) {
                  if ((a2 = n2 + Math.floor((r2 - n2) / 2)) === i3 || e4 > t3[a2].lastSample.originalDts && e4 < t3[a2 + 1].originalBeginDts) {
                    o2 = a2;
                    break;
                  }
                  t3[a2].originalBeginDts < e4 ? n2 = a2 + 1 : r2 = a2 - 1;
                }
                return o2;
              }, e3.prototype._searchNearestSegmentAfter = function(e4) {
                return this._searchNearestSegmentBefore(e4) + 1;
              }, e3.prototype.append = function(e4) {
                var t3 = this._list, i3 = e4, a2 = this._lastAppendLocation, n2 = 0;
                -1 !== a2 && a2 < t3.length && i3.originalBeginDts >= t3[a2].lastSample.originalDts && (a2 === t3.length - 1 || a2 < t3.length - 1 && i3.originalBeginDts < t3[a2 + 1].originalBeginDts) ? n2 = a2 + 1 : t3.length > 0 && (n2 = this._searchNearestSegmentBefore(i3.originalBeginDts) + 1), this._lastAppendLocation = n2, this._list.splice(n2, 0, i3);
              }, e3.prototype.getLastSegmentBefore = function(e4) {
                var t3 = this._searchNearestSegmentBefore(e4);
                return t3 >= 0 ? this._list[t3] : null;
              }, e3.prototype.getLastSampleBefore = function(e4) {
                var t3 = this.getLastSegmentBefore(e4);
                return null != t3 ? t3.lastSample : null;
              }, e3.prototype.getLastSyncPointBefore = function(e4) {
                for (var t3 = this._searchNearestSegmentBefore(e4), i3 = this._list[t3].syncPoints; 0 === i3.length && t3 > 0; ) t3--, i3 = this._list[t3].syncPoints;
                return i3.length > 0 ? i3[i3.length - 1] : null;
              }, e3;
            })();
          }, 346: function(e2, t2, i2) {
            "use strict";
            i2.r(t2);
            var a = i2(7), n = i2.n(a), r = i2(856), o = i2(994), s = i2(403), d = i2(867), _ = (function() {
              function e3(e4) {
                this.TAG = "MSEController", this._config = e4, this._emitter = new (n())(), this._config.isLive && null == this._config.autoCleanupSourceBuffer && (this._config.autoCleanupSourceBuffer = true), this.e = { onSourceOpen: this._onSourceOpen.bind(this), onSourceEnded: this._onSourceEnded.bind(this), onSourceClose: this._onSourceClose.bind(this), onStartStreaming: this._onStartStreaming.bind(this), onEndStreaming: this._onEndStreaming.bind(this), onQualityChange: this._onQualityChange.bind(this), onSourceBufferError: this._onSourceBufferError.bind(this), onSourceBufferUpdateEnd: this._onSourceBufferUpdateEnd.bind(this) }, this._useManagedMediaSource = "function" == typeof self.ManagedMediaSource && !("function" == typeof self.MediaSource), this._mediaSource = null, this._mediaSourceObjectURL = null, this._mediaElementProxy = null, this._isBufferFull = false, this._hasPendingEos = false, this._requireSetMediaDuration = false, this._pendingMediaDuration = 0, this._pendingSourceBufferInit = [], this._mimeTypes = { video: null, audio: null }, this._sourceBuffers = { video: null, audio: null }, this._lastInitSegments = { video: null, audio: null }, this._pendingSegments = { video: [], audio: [] }, this._pendingRemoveRanges = { video: [], audio: [] };
              }
              return e3.prototype.destroy = function() {
                this._mediaSource && this.shutdown(), this._mediaSourceObjectURL && this.revokeObjectURL(), this.e = null, this._emitter.removeAllListeners(), this._emitter = null;
              }, e3.prototype.on = function(e4, t3) {
                this._emitter.addListener(e4, t3);
              }, e3.prototype.off = function(e4, t3) {
                this._emitter.removeListener(e4, t3);
              }, e3.prototype.initialize = function(e4) {
                if (this._mediaSource) throw new d.IllegalStateException("MediaSource has been attached to an HTMLMediaElement!");
                this._useManagedMediaSource && r.default.v(this.TAG, "Using ManagedMediaSource");
                var t3 = this._mediaSource = this._useManagedMediaSource ? new self.ManagedMediaSource() : new self.MediaSource();
                t3.addEventListener("sourceopen", this.e.onSourceOpen), t3.addEventListener("sourceended", this.e.onSourceEnded), t3.addEventListener("sourceclose", this.e.onSourceClose), this._useManagedMediaSource && (t3.addEventListener("startstreaming", this.e.onStartStreaming), t3.addEventListener("endstreaming", this.e.onEndStreaming), t3.addEventListener("qualitychange", this.e.onQualityChange)), this._mediaElementProxy = e4;
              }, e3.prototype.shutdown = function() {
                if (this._mediaSource) {
                  var e4 = this._mediaSource;
                  for (var t3 in this._sourceBuffers) {
                    var i3 = this._pendingSegments[t3];
                    i3.splice(0, i3.length), this._pendingSegments[t3] = null, this._pendingRemoveRanges[t3] = null, this._lastInitSegments[t3] = null;
                    var a2 = this._sourceBuffers[t3];
                    if (a2) {
                      if ("closed" !== e4.readyState) {
                        try {
                          e4.removeSourceBuffer(a2);
                        } catch (e5) {
                          r.default.e(this.TAG, e5.message);
                        }
                        a2.removeEventListener("error", this.e.onSourceBufferError), a2.removeEventListener("updateend", this.e.onSourceBufferUpdateEnd);
                      }
                      this._mimeTypes[t3] = null, this._sourceBuffers[t3] = null;
                    }
                  }
                  if ("open" === e4.readyState) try {
                    e4.endOfStream();
                  } catch (e5) {
                    r.default.e(this.TAG, e5.message);
                  }
                  this._mediaElementProxy = null, e4.removeEventListener("sourceopen", this.e.onSourceOpen), e4.removeEventListener("sourceended", this.e.onSourceEnded), e4.removeEventListener("sourceclose", this.e.onSourceClose), this._useManagedMediaSource && (e4.removeEventListener("startstreaming", this.e.onStartStreaming), e4.removeEventListener("endstreaming", this.e.onEndStreaming), e4.removeEventListener("qualitychange", this.e.onQualityChange)), this._pendingSourceBufferInit = [], this._isBufferFull = false, this._mediaSource = null;
                }
              }, e3.prototype.isManagedMediaSource = function() {
                return this._useManagedMediaSource;
              }, e3.prototype.getObject = function() {
                if (!this._mediaSource) throw new d.IllegalStateException("MediaSource has not been initialized yet!");
                return this._mediaSource;
              }, e3.prototype.getHandle = function() {
                if (!this._mediaSource) throw new d.IllegalStateException("MediaSource has not been initialized yet!");
                return this._mediaSource.handle;
              }, e3.prototype.getObjectURL = function() {
                if (!this._mediaSource) throw new d.IllegalStateException("MediaSource has not been initialized yet!");
                return null == this._mediaSourceObjectURL && (this._mediaSourceObjectURL = URL.createObjectURL(this._mediaSource)), this._mediaSourceObjectURL;
              }, e3.prototype.revokeObjectURL = function() {
                this._mediaSourceObjectURL && (URL.revokeObjectURL(this._mediaSourceObjectURL), this._mediaSourceObjectURL = null);
              }, e3.prototype.appendInitSegment = function(e4, t3) {
                if (void 0 === t3 && (t3 = void 0), !this._mediaSource || "open" !== this._mediaSource.readyState || false === this._mediaSource.streaming) return this._pendingSourceBufferInit.push(e4), void this._pendingSegments[e4.type].push(e4);
                var i3 = e4, a2 = "".concat(i3.container);
                i3.codec && i3.codec.length > 0 && ("opus" === i3.codec && o.default.safari && (i3.codec = "Opus"), a2 += ";codecs=".concat(i3.codec));
                var n2 = false;
                if (r.default.v(this.TAG, "Received Initialization Segment, mimeType: " + a2), this._lastInitSegments[i3.type] = i3, a2 !== this._mimeTypes[i3.type]) {
                  if (this._mimeTypes[i3.type]) r.default.v(this.TAG, "Notice: ".concat(i3.type, " mimeType changed, origin: ").concat(this._mimeTypes[i3.type], ", target: ").concat(a2));
                  else {
                    n2 = true;
                    try {
                      var d2 = this._sourceBuffers[i3.type] = this._mediaSource.addSourceBuffer(a2);
                      d2.addEventListener("error", this.e.onSourceBufferError), d2.addEventListener("updateend", this.e.onSourceBufferUpdateEnd);
                    } catch (e5) {
                      return r.default.e(this.TAG, e5.message), void this._emitter.emit(s.default.ERROR, { code: e5.code, msg: e5.message });
                    }
                  }
                  this._mimeTypes[i3.type] = a2;
                }
                t3 || this._pendingSegments[i3.type].push(i3), n2 || this._sourceBuffers[i3.type] && !this._sourceBuffers[i3.type].updating && this._doAppendSegments(), o.default.safari && "audio/mpeg" === i3.container && i3.mediaDuration > 0 && (this._requireSetMediaDuration = true, this._pendingMediaDuration = i3.mediaDuration / 1e3, this._updateMediaSourceDuration());
              }, e3.prototype.appendMediaSegment = function(e4) {
                var t3 = e4;
                this._pendingSegments[t3.type].push(t3), this._config.autoCleanupSourceBuffer && this._needCleanupSourceBuffer() && this._doCleanupSourceBuffer();
                var i3 = this._sourceBuffers[t3.type];
                !i3 || i3.updating || this._hasPendingRemoveRanges() || this._doAppendSegments();
              }, e3.prototype.flush = function() {
                for (var e4 in this._sourceBuffers) if (this._sourceBuffers[e4]) {
                  var t3 = this._sourceBuffers[e4];
                  if ("open" === this._mediaSource.readyState) try {
                    t3.abort();
                  } catch (e5) {
                    r.default.e(this.TAG, e5.message);
                  }
                  var i3 = this._pendingSegments[e4];
                  if (i3.splice(0, i3.length), "closed" !== this._mediaSource.readyState) {
                    for (var a2 = 0; a2 < t3.buffered.length; a2++) {
                      var n2 = t3.buffered.start(a2), s2 = t3.buffered.end(a2);
                      this._pendingRemoveRanges[e4].push({ start: n2, end: s2 });
                    }
                    if (t3.updating || this._doRemoveRanges(), o.default.safari) {
                      var d2 = this._lastInitSegments[e4];
                      d2 && (this._pendingSegments[e4].push(d2), t3.updating || this._doAppendSegments());
                    }
                  }
                }
              }, e3.prototype.endOfStream = function() {
                var e4 = this._mediaSource, t3 = this._sourceBuffers;
                e4 && "open" === e4.readyState ? t3.video && t3.video.updating || t3.audio && t3.audio.updating ? this._hasPendingEos = true : (this._hasPendingEos = false, e4.endOfStream()) : e4 && "closed" === e4.readyState && this._hasPendingSegments() && (this._hasPendingEos = true);
              }, e3.prototype._needCleanupSourceBuffer = function() {
                if (!this._config.autoCleanupSourceBuffer) return false;
                var e4 = this._mediaElementProxy.getCurrentTime();
                for (var t3 in this._sourceBuffers) {
                  var i3 = this._sourceBuffers[t3];
                  if (i3) {
                    var a2 = i3.buffered;
                    if (a2.length >= 1 && e4 - a2.start(0) >= this._config.autoCleanupMaxBackwardDuration) return true;
                  }
                }
                return false;
              }, e3.prototype._doCleanupSourceBuffer = function() {
                var e4 = this._mediaElementProxy.getCurrentTime();
                for (var t3 in this._sourceBuffers) {
                  var i3 = this._sourceBuffers[t3];
                  if (i3) {
                    for (var a2 = i3.buffered, n2 = false, r2 = 0; r2 < a2.length; r2++) {
                      var o2 = a2.start(r2), s2 = a2.end(r2);
                      if (o2 <= e4 && e4 < s2 + 3) {
                        if (e4 - o2 >= this._config.autoCleanupMaxBackwardDuration) {
                          n2 = true;
                          var d2 = e4 - this._config.autoCleanupMinBackwardDuration;
                          this._pendingRemoveRanges[t3].push({ start: o2, end: d2 });
                        }
                      } else s2 < e4 && (n2 = true, this._pendingRemoveRanges[t3].push({ start: o2, end: s2 }));
                    }
                    n2 && !i3.updating && this._doRemoveRanges();
                  }
                }
              }, e3.prototype._updateMediaSourceDuration = function() {
                var e4 = this._sourceBuffers;
                if (0 !== this._mediaElementProxy.getReadyState() && "open" === this._mediaSource.readyState && !(e4.video && e4.video.updating || e4.audio && e4.audio.updating)) {
                  var t3 = this._mediaSource.duration, i3 = this._pendingMediaDuration;
                  i3 > 0 && (isNaN(t3) || i3 > t3) && (r.default.v(this.TAG, "Update MediaSource duration from ".concat(t3, " to ").concat(i3)), this._mediaSource.duration = i3), this._requireSetMediaDuration = false, this._pendingMediaDuration = 0;
                }
              }, e3.prototype._doRemoveRanges = function() {
                for (var e4 in this._pendingRemoveRanges) if (this._sourceBuffers[e4] && !this._sourceBuffers[e4].updating) for (var t3 = this._sourceBuffers[e4], i3 = this._pendingRemoveRanges[e4]; i3.length && !t3.updating; ) {
                  var a2 = i3.shift();
                  t3.remove(a2.start, a2.end);
                }
              }, e3.prototype._doAppendSegments = function() {
                var e4 = this._pendingSegments;
                for (var t3 in e4) if (this._sourceBuffers[t3] && !this._sourceBuffers[t3].updating && false !== this._mediaSource.streaming && e4[t3].length > 0) {
                  var i3 = e4[t3].shift();
                  if ("number" == typeof i3.timestampOffset && isFinite(i3.timestampOffset)) {
                    var a2 = this._sourceBuffers[t3].timestampOffset, n2 = i3.timestampOffset / 1e3;
                    Math.abs(a2 - n2) > 0.1 && (r.default.v(this.TAG, "Update MPEG audio timestampOffset from ".concat(a2, " to ").concat(n2)), this._sourceBuffers[t3].timestampOffset = n2), delete i3.timestampOffset;
                  }
                  if (!i3.data || 0 === i3.data.byteLength) continue;
                  try {
                    this._sourceBuffers[t3].appendBuffer(i3.data), this._isBufferFull = false;
                  } catch (e5) {
                    this._pendingSegments[t3].unshift(i3), 22 === e5.code ? (this._isBufferFull || this._emitter.emit(s.default.BUFFER_FULL), this._isBufferFull = true) : (r.default.e(this.TAG, e5.message), this._emitter.emit(s.default.ERROR, { code: e5.code, msg: e5.message }));
                  }
                }
              }, e3.prototype._onSourceOpen = function() {
                if (r.default.v(this.TAG, "MediaSource onSourceOpen"), this._mediaSource.removeEventListener("sourceopen", this.e.onSourceOpen), this._pendingSourceBufferInit.length > 0) for (var e4 = this._pendingSourceBufferInit; e4.length; ) {
                  var t3 = e4.shift();
                  this.appendInitSegment(t3, true);
                }
                this._hasPendingSegments() && this._doAppendSegments(), this._emitter.emit(s.default.SOURCE_OPEN);
              }, e3.prototype._onStartStreaming = function() {
                r.default.v(this.TAG, "ManagedMediaSource onStartStreaming"), this._emitter.emit(s.default.START_STREAMING);
              }, e3.prototype._onEndStreaming = function() {
                r.default.v(this.TAG, "ManagedMediaSource onEndStreaming"), this._emitter.emit(s.default.END_STREAMING);
              }, e3.prototype._onQualityChange = function() {
                r.default.v(this.TAG, "ManagedMediaSource onQualityChange");
              }, e3.prototype._onSourceEnded = function() {
                r.default.v(this.TAG, "MediaSource onSourceEnded");
              }, e3.prototype._onSourceClose = function() {
                r.default.v(this.TAG, "MediaSource onSourceClose"), this._mediaSource && null != this.e && (this._mediaSource.removeEventListener("sourceopen", this.e.onSourceOpen), this._mediaSource.removeEventListener("sourceended", this.e.onSourceEnded), this._mediaSource.removeEventListener("sourceclose", this.e.onSourceClose), this._useManagedMediaSource && (this._mediaSource.removeEventListener("startstreaming", this.e.onStartStreaming), this._mediaSource.removeEventListener("endstreaming", this.e.onEndStreaming), this._mediaSource.removeEventListener("qualitychange", this.e.onQualityChange)));
              }, e3.prototype._hasPendingSegments = function() {
                var e4 = this._pendingSegments;
                return e4.video.length > 0 || e4.audio.length > 0;
              }, e3.prototype._hasPendingRemoveRanges = function() {
                var e4 = this._pendingRemoveRanges;
                return e4.video.length > 0 || e4.audio.length > 0;
              }, e3.prototype._onSourceBufferUpdateEnd = function() {
                this._requireSetMediaDuration ? this._updateMediaSourceDuration() : this._hasPendingRemoveRanges() ? this._doRemoveRanges() : this._hasPendingSegments() ? this._doAppendSegments() : this._hasPendingEos && this.endOfStream(), this._emitter.emit(s.default.UPDATE_END);
              }, e3.prototype._onSourceBufferError = function(e4) {
                r.default.e(this.TAG, "SourceBuffer Error: ".concat(e4));
              }, e3;
            })();
            t2.default = _;
          }, 527: function(e2, t2, i2) {
            "use strict";
            i2.r(t2);
            var a = i2(7), n = i2.n(a), r = i2(861), o = i2.n(r), s = i2(856), d = i2(947), _ = i2(886), l = i2(726), u = (i2(137), i2(955)), c = (function() {
              function e3(e4, t3) {
                if (this.TAG = "Transmuxer", this._emitter = new (n())(), t3.enableWorker && "undefined" != typeof Worker) try {
                  this._worker = o()(137), this._workerDestroying = false, this._worker.addEventListener("message", this._onWorkerMessage.bind(this)), this._worker.postMessage({ cmd: "init", param: [e4, t3] }), this.e = { onLoggingConfigChanged: this._onLoggingConfigChanged.bind(this) }, d.default.registerListener(this.e.onLoggingConfigChanged), this._worker.postMessage({ cmd: "logging_config", param: d.default.getConfig() });
                } catch (i4) {
                  s.default.e(this.TAG, "Error while initialize transmuxing worker, fallback to inline transmuxing"), this._worker = null, this._controller = new _.default(e4, t3);
                }
                else this._controller = new _.default(e4, t3);
                if (this._controller) {
                  var i3 = this._controller;
                  i3.on(l.default.IO_ERROR, this._onIOError.bind(this)), i3.on(l.default.DEMUX_ERROR, this._onDemuxError.bind(this)), i3.on(l.default.INIT_SEGMENT, this._onInitSegment.bind(this)), i3.on(l.default.MEDIA_SEGMENT, this._onMediaSegment.bind(this)), i3.on(l.default.LOADING_COMPLETE, this._onLoadingComplete.bind(this)), i3.on(l.default.RECOVERED_EARLY_EOF, this._onRecoveredEarlyEof.bind(this)), i3.on(l.default.MEDIA_INFO, this._onMediaInfo.bind(this)), i3.on(l.default.METADATA_ARRIVED, this._onMetaDataArrived.bind(this)), i3.on(l.default.SCRIPTDATA_ARRIVED, this._onScriptDataArrived.bind(this)), i3.on(l.default.TIMED_ID3_METADATA_ARRIVED, this._onTimedID3MetadataArrived.bind(this)), i3.on(l.default.SYNCHRONOUS_KLV_METADATA_ARRIVED, this._onSynchronousKLVMetadataArrived.bind(this)), i3.on(l.default.ASYNCHRONOUS_KLV_METADATA_ARRIVED, this._onAsynchronousKLVMetadataArrived.bind(this)), i3.on(l.default.SMPTE2038_METADATA_ARRIVED, this._onSMPTE2038MetadataArrived.bind(this)), i3.on(l.default.SEI_ARRIVED, this._onSEIArrived.bind(this)), i3.on(l.default.SCTE35_METADATA_ARRIVED, this._onSCTE35MetadataArrived.bind(this)), i3.on(l.default.PES_PRIVATE_DATA_DESCRIPTOR, this._onPESPrivateDataDescriptor.bind(this)), i3.on(l.default.PES_PRIVATE_DATA_ARRIVED, this._onPESPrivateDataArrived.bind(this)), i3.on(l.default.STATISTICS_INFO, this._onStatisticsInfo.bind(this)), i3.on(l.default.RECOMMEND_SEEKPOINT, this._onRecommendSeekpoint.bind(this));
                }
              }
              return e3.prototype.destroy = function() {
                this._worker ? this._workerDestroying || (this._workerDestroying = true, this._worker.postMessage({ cmd: "destroy" }), d.default.removeListener(this.e.onLoggingConfigChanged), this.e = null) : (this._controller.destroy(), this._controller = null), this._emitter.removeAllListeners(), this._emitter = null;
              }, e3.prototype.on = function(e4, t3) {
                this._emitter.addListener(e4, t3);
              }, e3.prototype.off = function(e4, t3) {
                this._emitter.removeListener(e4, t3);
              }, e3.prototype.hasWorker = function() {
                return null != this._worker;
              }, e3.prototype.open = function() {
                this._worker ? this._worker.postMessage({ cmd: "start" }) : this._controller.start();
              }, e3.prototype.close = function() {
                this._worker ? this._worker.postMessage({ cmd: "stop" }) : this._controller.stop();
              }, e3.prototype.seek = function(e4) {
                this._worker ? this._worker.postMessage({ cmd: "seek", param: e4 }) : this._controller.seek(e4);
              }, e3.prototype.pause = function() {
                this._worker ? this._worker.postMessage({ cmd: "pause" }) : this._controller.pause();
              }, e3.prototype.resume = function() {
                this._worker ? this._worker.postMessage({ cmd: "resume" }) : this._controller.resume();
              }, e3.prototype._onInitSegment = function(e4, t3) {
                var i3 = this;
                Promise.resolve().then(function() {
                  i3._emitter.emit(l.default.INIT_SEGMENT, e4, t3);
                });
              }, e3.prototype._onMediaSegment = function(e4, t3) {
                var i3 = this;
                Promise.resolve().then(function() {
                  i3._emitter.emit(l.default.MEDIA_SEGMENT, e4, t3);
                });
              }, e3.prototype._onLoadingComplete = function() {
                var e4 = this;
                Promise.resolve().then(function() {
                  e4._emitter.emit(l.default.LOADING_COMPLETE);
                });
              }, e3.prototype._onRecoveredEarlyEof = function() {
                var e4 = this;
                Promise.resolve().then(function() {
                  e4._emitter.emit(l.default.RECOVERED_EARLY_EOF);
                });
              }, e3.prototype._onMediaInfo = function(e4) {
                var t3 = this;
                Promise.resolve().then(function() {
                  t3._emitter.emit(l.default.MEDIA_INFO, e4);
                });
              }, e3.prototype._onMetaDataArrived = function(e4) {
                var t3 = this;
                Promise.resolve().then(function() {
                  t3._emitter.emit(l.default.METADATA_ARRIVED, e4);
                });
              }, e3.prototype._onScriptDataArrived = function(e4) {
                var t3 = this;
                Promise.resolve().then(function() {
                  t3._emitter.emit(l.default.SCRIPTDATA_ARRIVED, e4);
                });
              }, e3.prototype._onTimedID3MetadataArrived = function(e4) {
                var t3 = this;
                Promise.resolve().then(function() {
                  t3._emitter.emit(l.default.TIMED_ID3_METADATA_ARRIVED, e4);
                });
              }, e3.prototype._onPGSSubtitleArrived = function(e4) {
                var t3 = this;
                Promise.resolve().then(function() {
                  t3._emitter.emit(l.default.PGS_SUBTITLE_ARRIVED, e4);
                });
              }, e3.prototype._onSynchronousKLVMetadataArrived = function(e4) {
                var t3 = this;
                Promise.resolve().then(function() {
                  t3._emitter.emit(l.default.SYNCHRONOUS_KLV_METADATA_ARRIVED, e4);
                });
              }, e3.prototype._onAsynchronousKLVMetadataArrived = function(e4) {
                var t3 = this;
                Promise.resolve().then(function() {
                  t3._emitter.emit(l.default.ASYNCHRONOUS_KLV_METADATA_ARRIVED, e4);
                });
              }, e3.prototype._onSMPTE2038MetadataArrived = function(e4) {
                var t3 = this;
                Promise.resolve().then(function() {
                  t3._emitter.emit(l.default.SMPTE2038_METADATA_ARRIVED, e4);
                });
              }, e3.prototype._onSEIArrived = function(e4) {
                var t3 = this;
                Promise.resolve().then(function() {
                  t3._emitter.emit(l.default.SEI_ARRIVED, e4);
                });
              }, e3.prototype._onSCTE35MetadataArrived = function(e4) {
                var t3 = this;
                Promise.resolve().then(function() {
                  t3._emitter.emit(l.default.SCTE35_METADATA_ARRIVED, e4);
                });
              }, e3.prototype._onPESPrivateDataDescriptor = function(e4) {
                var t3 = this;
                Promise.resolve().then(function() {
                  t3._emitter.emit(l.default.PES_PRIVATE_DATA_DESCRIPTOR, e4);
                });
              }, e3.prototype._onPESPrivateDataArrived = function(e4) {
                var t3 = this;
                Promise.resolve().then(function() {
                  t3._emitter.emit(l.default.PES_PRIVATE_DATA_ARRIVED, e4);
                });
              }, e3.prototype._onStatisticsInfo = function(e4) {
                var t3 = this;
                Promise.resolve().then(function() {
                  t3._emitter.emit(l.default.STATISTICS_INFO, e4);
                });
              }, e3.prototype._onIOError = function(e4, t3) {
                var i3 = this;
                Promise.resolve().then(function() {
                  i3._emitter.emit(l.default.IO_ERROR, e4, t3);
                });
              }, e3.prototype._onDemuxError = function(e4, t3) {
                var i3 = this;
                Promise.resolve().then(function() {
                  i3._emitter.emit(l.default.DEMUX_ERROR, e4, t3);
                });
              }, e3.prototype._onRecommendSeekpoint = function(e4) {
                var t3 = this;
                Promise.resolve().then(function() {
                  t3._emitter.emit(l.default.RECOMMEND_SEEKPOINT, e4);
                });
              }, e3.prototype._onLoggingConfigChanged = function(e4) {
                this._worker && this._worker.postMessage({ cmd: "logging_config", param: e4 });
              }, e3.prototype._onWorkerMessage = function(e4) {
                var t3 = e4.data, i3 = t3.data;
                if ("destroyed" === t3.msg || this._workerDestroying) return this._workerDestroying = false, this._worker.terminate(), void (this._worker = null);
                switch (t3.msg) {
                  case l.default.INIT_SEGMENT:
                  case l.default.MEDIA_SEGMENT:
                    this._emitter.emit(t3.msg, i3.type, i3.data);
                    break;
                  case l.default.LOADING_COMPLETE:
                  case l.default.RECOVERED_EARLY_EOF:
                    this._emitter.emit(t3.msg);
                    break;
                  case l.default.MEDIA_INFO:
                    Object.setPrototypeOf(i3, u.default.prototype), this._emitter.emit(t3.msg, i3);
                    break;
                  case l.default.METADATA_ARRIVED:
                  case l.default.SCRIPTDATA_ARRIVED:
                  case l.default.TIMED_ID3_METADATA_ARRIVED:
                  case l.default.PGS_SUBTITLE_ARRIVED:
                  case l.default.SYNCHRONOUS_KLV_METADATA_ARRIVED:
                  case l.default.ASYNCHRONOUS_KLV_METADATA_ARRIVED:
                  case l.default.SMPTE2038_METADATA_ARRIVED:
                  case l.default.SCTE35_METADATA_ARRIVED:
                  case l.default.SEI_ARRIVED:
                  case l.default.PES_PRIVATE_DATA_DESCRIPTOR:
                  case l.default.PES_PRIVATE_DATA_ARRIVED:
                  case l.default.STATISTICS_INFO:
                    this._emitter.emit(t3.msg, i3);
                    break;
                  case l.default.IO_ERROR:
                  case l.default.DEMUX_ERROR:
                    this._emitter.emit(t3.msg, i3.type, i3.info);
                    break;
                  case l.default.RECOMMEND_SEEKPOINT:
                    this._emitter.emit(t3.msg, i3);
                    break;
                  case "logcat_callback":
                    s.default.emitter.emit("log", i3.type, i3.logcat);
                }
              }, e3;
            })();
            t2.default = c;
          }, 886: function(e2, t2, i2) {
            "use strict";
            i2.r(t2), i2.d(t2, { default: function() {
              return Ge;
            } });
            var a = i2(7), n = i2.n(a), r = i2(856), o = i2(994), s = i2(955);
            function d(e3, t3, i3) {
              var a2 = e3;
              if (t3 + i3 < a2.length) {
                for (; i3--; ) if (128 != (192 & a2[++t3])) return false;
                return true;
              }
              return false;
            }
            var _, l = function(e3) {
              for (var t3 = [], i3 = e3, a2 = 0, n2 = e3.length; a2 < n2; ) if (i3[a2] < 128) t3.push(String.fromCharCode(i3[a2])), ++a2;
              else {
                if (i3[a2] < 192) ;
                else if (i3[a2] < 224) {
                  if (d(i3, a2, 1) && (r2 = (31 & i3[a2]) << 6 | 63 & i3[a2 + 1]) >= 128) {
                    t3.push(String.fromCharCode(65535 & r2)), a2 += 2;
                    continue;
                  }
                } else if (i3[a2] < 240) {
                  if (d(i3, a2, 2) && (r2 = (15 & i3[a2]) << 12 | (63 & i3[a2 + 1]) << 6 | 63 & i3[a2 + 2]) >= 2048 && 55296 != (63488 & r2)) {
                    t3.push(String.fromCharCode(65535 & r2)), a2 += 3;
                    continue;
                  }
                } else if (i3[a2] < 248) {
                  var r2;
                  if (d(i3, a2, 3) && (r2 = (7 & i3[a2]) << 18 | (63 & i3[a2 + 1]) << 12 | (63 & i3[a2 + 2]) << 6 | 63 & i3[a2 + 3]) > 65536 && r2 < 1114112) {
                    r2 -= 65536, t3.push(String.fromCharCode(r2 >>> 10 | 55296)), t3.push(String.fromCharCode(1023 & r2 | 56320)), a2 += 4;
                    continue;
                  }
                }
                t3.push(String.fromCharCode(65533)), ++a2;
              }
              return t3.join("");
            }, u = i2(867), c = (_ = new ArrayBuffer(2), new DataView(_).setInt16(0, 256, true), 256 === new Int16Array(_)[0]), h = (function() {
              function e3() {
              }
              return e3.parseScriptData = function(t3, i3, a2) {
                var n2 = {};
                try {
                  var o2 = e3.parseValue(t3, i3, a2), s2 = e3.parseValue(t3, i3 + o2.size, a2 - o2.size);
                  n2[o2.data] = s2.data;
                } catch (e4) {
                  r.default.e("AMF", e4.toString());
                }
                return n2;
              }, e3.parseObject = function(t3, i3, a2) {
                if (a2 < 3) throw new u.IllegalStateException("Data not enough when parse ScriptDataObject");
                var n2 = e3.parseString(t3, i3, a2), r2 = e3.parseValue(t3, i3 + n2.size, a2 - n2.size), o2 = r2.objectEnd;
                return { data: { name: n2.data, value: r2.data }, size: n2.size + r2.size, objectEnd: o2 };
              }, e3.parseVariable = function(t3, i3, a2) {
                return e3.parseObject(t3, i3, a2);
              }, e3.parseString = function(e4, t3, i3) {
                if (i3 < 2) throw new u.IllegalStateException("Data not enough when parse String");
                var a2 = new DataView(e4, t3, i3).getUint16(0, !c);
                return { data: a2 > 0 ? l(new Uint8Array(e4, t3 + 2, a2)) : "", size: 2 + a2 };
              }, e3.parseLongString = function(e4, t3, i3) {
                if (i3 < 4) throw new u.IllegalStateException("Data not enough when parse LongString");
                var a2 = new DataView(e4, t3, i3).getUint32(0, !c);
                return { data: a2 > 0 ? l(new Uint8Array(e4, t3 + 4, a2)) : "", size: 4 + a2 };
              }, e3.parseDate = function(e4, t3, i3) {
                if (i3 < 10) throw new u.IllegalStateException("Data size invalid when parse Date");
                var a2 = new DataView(e4, t3, i3), n2 = a2.getFloat64(0, !c), r2 = a2.getInt16(8, !c);
                return { data: new Date(n2 += 60 * r2 * 1e3), size: 10 };
              }, e3.parseValue = function(t3, i3, a2) {
                if (a2 < 1) throw new u.IllegalStateException("Data not enough when parse Value");
                var n2, o2 = new DataView(t3, i3, a2), s2 = 1, d2 = o2.getUint8(0), _2 = false;
                try {
                  switch (d2) {
                    case 0:
                      n2 = o2.getFloat64(1, !c), s2 += 8;
                      break;
                    case 1:
                      n2 = !!o2.getUint8(1), s2 += 1;
                      break;
                    case 2:
                      var l2 = e3.parseString(t3, i3 + 1, a2 - 1);
                      n2 = l2.data, s2 += l2.size;
                      break;
                    case 3:
                      n2 = {};
                      var h2 = 0;
                      for (9 == (16777215 & o2.getUint32(a2 - 4, !c)) && (h2 = 3); s2 < a2 - 4; ) {
                        var f2 = e3.parseObject(t3, i3 + s2, a2 - s2 - h2);
                        if (f2.objectEnd) break;
                        n2[f2.data.name] = f2.data.value, s2 += f2.size;
                      }
                      s2 <= a2 - 3 && 9 == (16777215 & o2.getUint32(s2 - 1, !c)) && (s2 += 3);
                      break;
                    case 8:
                      for (n2 = {}, s2 += 4, h2 = 0, 9 == (16777215 & o2.getUint32(a2 - 4, !c)) && (h2 = 3); s2 < a2 - 8; ) {
                        var p2 = e3.parseVariable(t3, i3 + s2, a2 - s2 - h2);
                        if (p2.objectEnd) break;
                        n2[p2.data.name] = p2.data.value, s2 += p2.size;
                      }
                      s2 <= a2 - 3 && 9 == (16777215 & o2.getUint32(s2 - 1, !c)) && (s2 += 3);
                      break;
                    case 9:
                      n2 = void 0, s2 = 1, _2 = true;
                      break;
                    case 10:
                      n2 = [];
                      var m2 = o2.getUint32(1, !c);
                      s2 += 4;
                      for (var g2 = 0; g2 < m2; g2++) {
                        var v2 = e3.parseValue(t3, i3 + s2, a2 - s2);
                        n2.push(v2.data), s2 += v2.size;
                      }
                      break;
                    case 11:
                      var y2 = e3.parseDate(t3, i3 + 1, a2 - 1);
                      n2 = y2.data, s2 += y2.size;
                      break;
                    case 12:
                      var S2 = e3.parseString(t3, i3 + 1, a2 - 1);
                      n2 = S2.data, s2 += S2.size;
                      break;
                    default:
                      s2 = a2, r.default.w("AMF", "Unsupported AMF value type " + d2);
                  }
                } catch (e4) {
                  r.default.e("AMF", e4.toString());
                }
                return { data: n2, size: s2, objectEnd: _2 };
              }, e3;
            })(), f = (function() {
              function e3(e4) {
                this.TAG = "ExpGolomb", this._buffer = e4, this._buffer_index = 0, this._total_bytes = e4.byteLength, this._total_bits = 8 * e4.byteLength, this._current_word = 0, this._current_word_bits_left = 0;
              }
              return e3.prototype.destroy = function() {
                this._buffer = null;
              }, e3.prototype._fillCurrentWord = function() {
                var e4 = this._total_bytes - this._buffer_index;
                if (e4 <= 0) throw new u.IllegalStateException("ExpGolomb: _fillCurrentWord() but no bytes available");
                var t3 = Math.min(4, e4), i3 = new Uint8Array(4);
                i3.set(this._buffer.subarray(this._buffer_index, this._buffer_index + t3)), this._current_word = new DataView(i3.buffer).getUint32(0, false), this._buffer_index += t3, this._current_word_bits_left = 8 * t3;
              }, e3.prototype.readBits = function(e4) {
                if (e4 > 32) throw new u.InvalidArgumentException("ExpGolomb: readBits() bits exceeded max 32bits!");
                if (e4 <= this._current_word_bits_left) {
                  var t3 = this._current_word >>> 32 - e4;
                  return this._current_word <<= e4, this._current_word_bits_left -= e4, t3;
                }
                var i3 = this._current_word_bits_left ? this._current_word : 0;
                i3 >>>= 32 - this._current_word_bits_left;
                var a2 = e4 - this._current_word_bits_left;
                this._fillCurrentWord();
                var n2 = Math.min(a2, this._current_word_bits_left), r2 = this._current_word >>> 32 - n2;
                return this._current_word <<= n2, this._current_word_bits_left -= n2, i3 << n2 | r2;
              }, e3.prototype.readBool = function() {
                return 1 === this.readBits(1);
              }, e3.prototype.readByte = function() {
                return this.readBits(8);
              }, e3.prototype._skipLeadingZero = function() {
                var e4;
                for (e4 = 0; e4 < this._current_word_bits_left; e4++) if (this._current_word & 2147483648 >>> e4) return this._current_word <<= e4, this._current_word_bits_left -= e4, e4;
                return this._fillCurrentWord(), e4 + this._skipLeadingZero();
              }, e3.prototype.readUEG = function() {
                var e4 = this._skipLeadingZero();
                return this.readBits(e4 + 1) - 1;
              }, e3.prototype.readSEG = function() {
                var e4 = this.readUEG();
                return 1 & e4 ? e4 + 1 >>> 1 : -1 * (e4 >>> 1);
              }, e3;
            })(), p = (function() {
              function e3() {
              }
              return e3._ebsp2rbsp = function(e4) {
                for (var t3 = e4, i3 = t3.byteLength, a2 = new Uint8Array(i3), n2 = 0, r2 = 0; r2 < i3; r2++) r2 >= 2 && 3 === t3[r2] && 0 === t3[r2 - 1] && 0 === t3[r2 - 2] || (a2[n2] = t3[r2], n2++);
                return new Uint8Array(a2.buffer, 0, n2);
              }, e3.parseSPS = function(t3) {
                for (var i3 = t3.subarray(1, 4), a2 = "avc1.", n2 = 0; n2 < 3; n2++) {
                  var r2 = i3[n2].toString(16);
                  r2.length < 2 && (r2 = "0" + r2), a2 += r2;
                }
                var o2 = e3._ebsp2rbsp(t3), s2 = new f(o2);
                s2.readByte();
                var d2 = s2.readByte();
                s2.readByte();
                var _2 = s2.readByte();
                s2.readUEG();
                var l2 = e3.getProfileString(d2), u2 = e3.getLevelString(_2), c2 = 1, h2 = 420, p2 = 8, m2 = 8;
                if ((100 === d2 || 110 === d2 || 122 === d2 || 244 === d2 || 44 === d2 || 83 === d2 || 86 === d2 || 118 === d2 || 128 === d2 || 138 === d2 || 144 === d2) && (3 === (c2 = s2.readUEG()) && s2.readBits(1), c2 <= 3 && (h2 = [0, 420, 422, 444][c2]), p2 = s2.readUEG() + 8, m2 = s2.readUEG() + 8, s2.readBits(1), s2.readBool())) for (var g2 = 3 !== c2 ? 8 : 12, v2 = 0; v2 < g2; v2++) s2.readBool() && (v2 < 6 ? e3._skipScalingList(s2, 16) : e3._skipScalingList(s2, 64));
                s2.readUEG();
                var y2 = s2.readUEG();
                if (0 === y2) s2.readUEG();
                else if (1 === y2) {
                  s2.readBits(1), s2.readSEG(), s2.readSEG();
                  var S2 = s2.readUEG();
                  for (v2 = 0; v2 < S2; v2++) s2.readSEG();
                }
                var E2 = s2.readUEG();
                s2.readBits(1);
                var A2 = s2.readUEG(), b2 = s2.readUEG(), R2 = s2.readBits(1);
                0 === R2 && s2.readBits(1), s2.readBits(1);
                var T2 = 0, L2 = 0, k2 = 0, D2 = 0;
                s2.readBool() && (T2 = s2.readUEG(), L2 = s2.readUEG(), k2 = s2.readUEG(), D2 = s2.readUEG());
                var M2 = 1, C2 = 1, w2 = 0, I2 = true, B2 = 0, O2 = 0;
                if (s2.readBool()) {
                  if (s2.readBool()) {
                    var P2 = s2.readByte();
                    P2 > 0 && P2 < 16 ? (M2 = [1, 12, 10, 16, 40, 24, 20, 32, 80, 18, 15, 64, 160, 4, 3, 2][P2 - 1], C2 = [1, 11, 11, 11, 33, 11, 11, 11, 33, 11, 11, 33, 99, 3, 2, 1][P2 - 1]) : 255 === P2 && (M2 = s2.readByte() << 8 | s2.readByte(), C2 = s2.readByte() << 8 | s2.readByte());
                  }
                  if (s2.readBool() && s2.readBool(), s2.readBool() && (s2.readBits(4), s2.readBool() && s2.readBits(24)), s2.readBool() && (s2.readUEG(), s2.readUEG()), s2.readBool()) {
                    var x2 = s2.readBits(32), U2 = s2.readBits(32);
                    I2 = s2.readBool(), w2 = (B2 = U2) / (O2 = 2 * x2);
                  }
                }
                var N2 = 1;
                1 === M2 && 1 === C2 || (N2 = M2 / C2);
                var V2 = 0, G2 = 0;
                0 === c2 ? (V2 = 1, G2 = 2 - R2) : (V2 = 3 === c2 ? 1 : 2, G2 = (1 === c2 ? 2 : 1) * (2 - R2));
                var F2 = 16 * (A2 + 1), j2 = 16 * (b2 + 1) * (2 - R2);
                F2 -= (T2 + L2) * V2, j2 -= (k2 + D2) * G2;
                var z2 = Math.ceil(F2 * N2);
                return s2.destroy(), s2 = null, { codec_mimetype: a2, profile_idc: d2, level_idc: _2, profile_string: l2, level_string: u2, chroma_format_idc: c2, bit_depth: p2, bit_depth_luma: p2, bit_depth_chroma: m2, ref_frames: E2, chroma_format: h2, chroma_format_string: e3.getChromaFormatString(h2), frame_rate: { fixed: I2, fps: w2, fps_den: O2, fps_num: B2 }, sar_ratio: { width: M2, height: C2 }, codec_size: { width: F2, height: j2 }, present_size: { width: z2, height: j2 } };
              }, e3._skipScalingList = function(e4, t3) {
                for (var i3 = 8, a2 = 8, n2 = 0; n2 < t3; n2++) 0 !== a2 && (a2 = (i3 + e4.readSEG() + 256) % 256), i3 = 0 === a2 ? i3 : a2;
              }, e3.getProfileString = function(e4) {
                switch (e4) {
                  case 66:
                    return "Baseline";
                  case 77:
                    return "Main";
                  case 88:
                    return "Extended";
                  case 100:
                    return "High";
                  case 110:
                    return "High10";
                  case 122:
                    return "High422";
                  case 244:
                    return "High444";
                  default:
                    return "Unknown";
                }
              }, e3.getLevelString = function(e4) {
                return (e4 / 10).toFixed(1);
              }, e3.getChromaFormatString = function(e4) {
                switch (e4) {
                  case 420:
                    return "4:2:0";
                  case 422:
                    return "4:2:2";
                  case 444:
                    return "4:4:4";
                  default:
                    return "Unknown";
                }
              }, e3;
            })(), m = i2(827), g = (function() {
              function e3() {
              }
              return e3._ebsp2rbsp = function(e4) {
                for (var t3 = e4, i3 = t3.byteLength, a2 = new Uint8Array(i3), n2 = 0, r2 = 0; r2 < i3; r2++) r2 >= 2 && 3 === t3[r2] && 0 === t3[r2 - 1] && 0 === t3[r2 - 2] || (a2[n2] = t3[r2], n2++);
                return new Uint8Array(a2.buffer, 0, n2);
              }, e3.parseVPS = function(t3) {
                var i3 = e3._ebsp2rbsp(t3), a2 = new f(i3);
                return a2.readByte(), a2.readByte(), a2.readBits(4), a2.readBits(2), a2.readBits(6), { num_temporal_layers: a2.readBits(3) + 1, temporal_id_nested: a2.readBool() };
              }, e3.parseSPS = function(t3) {
                var i3 = e3._ebsp2rbsp(t3), a2 = new f(i3);
                a2.readByte(), a2.readByte();
                for (var n2 = 0, r2 = 0, o2 = 0, s2 = 0, d2 = (a2.readBits(4), a2.readBits(3)), _2 = (a2.readBool(), a2.readBits(2)), l2 = a2.readBool(), u2 = a2.readBits(5), c2 = a2.readByte(), h2 = a2.readByte(), p2 = a2.readByte(), m2 = a2.readByte(), g2 = a2.readByte(), v2 = a2.readByte(), y2 = a2.readByte(), S2 = a2.readByte(), E2 = a2.readByte(), A2 = a2.readByte(), b2 = a2.readByte(), R2 = [], T2 = [], L2 = 0; L2 < d2; L2++) R2.push(a2.readBool()), T2.push(a2.readBool());
                if (d2 > 0) for (L2 = d2; L2 < 8; L2++) a2.readBits(2);
                for (L2 = 0; L2 < d2; L2++) R2[L2] && (a2.readByte(), a2.readByte(), a2.readByte(), a2.readByte(), a2.readByte(), a2.readByte(), a2.readByte(), a2.readByte(), a2.readByte(), a2.readByte(), a2.readByte()), T2[L2] && a2.readByte();
                a2.readUEG();
                var k2 = a2.readUEG();
                3 == k2 && a2.readBits(1);
                var D2 = a2.readUEG(), M2 = a2.readUEG();
                a2.readBool() && (n2 += a2.readUEG(), r2 += a2.readUEG(), o2 += a2.readUEG(), s2 += a2.readUEG());
                var C2 = a2.readUEG(), w2 = a2.readUEG(), I2 = a2.readUEG();
                for (L2 = a2.readBool() ? 0 : d2; L2 <= d2; L2++) a2.readUEG(), a2.readUEG(), a2.readUEG();
                if (a2.readUEG(), a2.readUEG(), a2.readUEG(), a2.readUEG(), a2.readUEG(), a2.readUEG(), a2.readBool() && a2.readBool()) for (var B2 = 0; B2 < 4; B2++) for (var O2 = 0; O2 < (3 === B2 ? 2 : 6); O2++) if (a2.readBool()) {
                  var P2 = Math.min(64, 1 << 4 + (B2 << 1));
                  for (B2 > 1 && a2.readSEG(), L2 = 0; L2 < P2; L2++) a2.readSEG();
                } else a2.readUEG();
                a2.readBool(), a2.readBool(), a2.readBool() && (a2.readByte(), a2.readUEG(), a2.readUEG(), a2.readBool());
                var x2 = a2.readUEG(), U2 = 0;
                for (L2 = 0; L2 < x2; L2++) {
                  var N2 = false;
                  if (0 !== L2 && (N2 = a2.readBool()), N2) {
                    L2 === x2 && a2.readUEG(), a2.readBool(), a2.readUEG();
                    for (var V2 = 0, G2 = 0; G2 <= U2; G2++) {
                      var F2 = a2.readBool(), j2 = false;
                      F2 || (j2 = a2.readBool()), (F2 || j2) && V2++;
                    }
                    U2 = V2;
                  } else {
                    var z2 = a2.readUEG(), H2 = a2.readUEG();
                    for (U2 = z2 + H2, G2 = 0; G2 < z2; G2++) a2.readUEG(), a2.readBool();
                    for (G2 = 0; G2 < H2; G2++) a2.readUEG(), a2.readBool();
                  }
                }
                if (a2.readBool()) {
                  var q2 = a2.readUEG();
                  for (L2 = 0; L2 < q2; L2++) {
                    for (G2 = 0; G2 < I2 + 4; G2++) a2.readBits(1);
                    a2.readBits(1);
                  }
                }
                var K2 = 0, W2 = 1, Y2 = 1, X2 = false, Q2 = 1, J2 = 1;
                if (a2.readBool(), a2.readBool(), a2.readBool()) {
                  if (a2.readBool()) {
                    var Z2 = a2.readByte();
                    Z2 > 0 && Z2 <= 16 ? (W2 = [1, 12, 10, 16, 40, 24, 20, 32, 80, 18, 15, 64, 160, 4, 3, 2][Z2 - 1], Y2 = [1, 11, 11, 11, 33, 11, 11, 11, 33, 11, 11, 33, 99, 3, 2, 1][Z2 - 1]) : 255 === Z2 && (W2 = a2.readBits(16), Y2 = a2.readBits(16));
                  }
                  if (a2.readBool() && a2.readBool(), a2.readBool() && (a2.readBits(3), a2.readBool(), a2.readBool() && (a2.readByte(), a2.readByte(), a2.readByte())), a2.readBool() && (a2.readUEG(), a2.readUEG()), a2.readBool(), a2.readBool(), a2.readBool(), a2.readBool() && (a2.readUEG(), a2.readUEG(), a2.readUEG(), a2.readUEG()), a2.readBool() && (Q2 = a2.readBits(32), J2 = a2.readBits(32), a2.readBool() && a2.readUEG(), a2.readBool())) {
                    var $2, ee2, te2 = false;
                    for ($2 = a2.readBool(), ee2 = a2.readBool(), ($2 || ee2) && ((te2 = a2.readBool()) && (a2.readByte(), a2.readBits(5), a2.readBool(), a2.readBits(5)), a2.readBits(4), a2.readBits(4), te2 && a2.readBits(4), a2.readBits(5), a2.readBits(5), a2.readBits(5)), L2 = 0; L2 <= d2; L2++) {
                      var ie2 = a2.readBool();
                      X2 = ie2;
                      var ae2 = true, ne2 = 1;
                      ie2 || (ae2 = a2.readBool());
                      var re2 = false;
                      if (ae2 ? a2.readUEG() : re2 = a2.readBool(), re2 || (ne2 = a2.readUEG() + 1), $2) {
                        for (G2 = 0; G2 < ne2; G2++) a2.readUEG(), a2.readUEG(), te2 && (a2.readUEG(), a2.readUEG());
                        a2.readBool();
                      }
                      if (ee2) {
                        for (G2 = 0; G2 < ne2; G2++) a2.readUEG(), a2.readUEG(), te2 && (a2.readUEG(), a2.readUEG());
                        a2.readBool();
                      }
                    }
                  }
                  a2.readBool() && (a2.readBool(), a2.readBool(), a2.readBool(), K2 = a2.readUEG(), a2.readUEG(), a2.readUEG(), a2.readUEG(), a2.readUEG());
                }
                a2.readBool();
                var oe2 = "hvc1.".concat(u2, ".1.L").concat(b2, ".B0"), se2 = D2 - (n2 + r2) * (1 === k2 || 2 === k2 ? 2 : 1), de2 = M2 - (o2 + s2) * (1 === k2 ? 2 : 1), _e2 = 1;
                return 1 !== W2 && 1 !== Y2 && (_e2 = W2 / Y2), a2.destroy(), a2 = null, { codec_mimetype: oe2, profile_string: e3.getProfileString(u2), level_string: e3.getLevelString(b2), profile_idc: u2, bit_depth: C2 + 8, ref_frames: 1, chroma_format: k2, chroma_format_string: e3.getChromaFormatString(k2), general_level_idc: b2, general_profile_space: _2, general_tier_flag: l2, general_profile_idc: u2, general_profile_compatibility_flags_1: c2, general_profile_compatibility_flags_2: h2, general_profile_compatibility_flags_3: p2, general_profile_compatibility_flags_4: m2, general_constraint_indicator_flags_1: g2, general_constraint_indicator_flags_2: v2, general_constraint_indicator_flags_3: y2, general_constraint_indicator_flags_4: S2, general_constraint_indicator_flags_5: E2, general_constraint_indicator_flags_6: A2, min_spatial_segmentation_idc: K2, constant_frame_rate: 0, chroma_format_idc: k2, bit_depth_luma_minus8: C2, bit_depth_chroma_minus8: w2, frame_rate: { fixed: X2, fps: J2 / Q2, fps_den: Q2, fps_num: J2 }, sar_ratio: { width: W2, height: Y2 }, codec_size: { width: se2, height: de2 }, present_size: { width: se2 * _e2, height: de2 } };
              }, e3.parsePPS = function(t3) {
                var i3 = e3._ebsp2rbsp(t3), a2 = new f(i3);
                a2.readByte(), a2.readByte(), a2.readUEG(), a2.readUEG(), a2.readBool(), a2.readBool(), a2.readBits(3), a2.readBool(), a2.readBool(), a2.readUEG(), a2.readUEG(), a2.readSEG(), a2.readBool(), a2.readBool(), a2.readBool() && a2.readUEG(), a2.readSEG(), a2.readSEG(), a2.readBool(), a2.readBool(), a2.readBool(), a2.readBool();
                var n2 = a2.readBool(), r2 = a2.readBool(), o2 = 1;
                return r2 && n2 ? o2 = 0 : r2 ? o2 = 3 : n2 && (o2 = 2), { parallelismType: o2 };
              }, e3.getChromaFormatString = function(e4) {
                switch (e4) {
                  case 0:
                    return "4:0:0";
                  case 1:
                    return "4:2:0";
                  case 2:
                    return "4:2:2";
                  case 3:
                    return "4:4:4";
                  default:
                    return "Unknown";
                }
              }, e3.getProfileString = function(e4) {
                switch (e4) {
                  case 1:
                    return "Main";
                  case 2:
                    return "Main10";
                  case 3:
                    return "MainSP";
                  case 4:
                    return "Rext";
                  case 9:
                    return "SCC";
                  default:
                    return "Unknown";
                }
              }, e3.getLevelString = function(e4) {
                return (e4 / 30).toFixed(1);
              }, e3;
            })();
            function v(e3) {
              return e3.byteOffset % 2 == 0 && e3.byteLength % 2 == 0;
            }
            function y(e3) {
              return e3.byteOffset % 4 == 0 && e3.byteLength % 4 == 0;
            }
            function S(e3, t3) {
              for (var i3 = 0; i3 < e3.length; i3++) if (e3[i3] !== t3[i3]) return false;
              return true;
            }
            var E = function(e3, t3) {
              return e3.byteLength === t3.byteLength && (y(e3) && y(t3) ? (function(e4, t4) {
                return S(new Uint32Array(e4.buffer, e4.byteOffset, e4.byteLength / 4), new Uint32Array(t4.buffer, t4.byteOffset, t4.byteLength / 4));
              })(e3, t3) : v(e3) && v(t3) ? (function(e4, t4) {
                return S(new Uint16Array(e4.buffer, e4.byteOffset, e4.byteLength / 2), new Uint16Array(t4.buffer, t4.byteOffset, t4.byteLength / 2));
              })(e3, t3) : (function(e4, t4) {
                return S(e4, t4);
              })(e3, t3));
            }, A = function() {
              return A = Object.assign || function(e3) {
                for (var t3, i3 = 1, a2 = arguments.length; i3 < a2; i3++) for (var n2 in t3 = arguments[i3]) Object.prototype.hasOwnProperty.call(t3, n2) && (e3[n2] = t3[n2]);
                return e3;
              }, A.apply(this, arguments);
            }, b = (function() {
              function e3() {
              }
              return e3.parseOBUs = function(t3, i3) {
                for (var a2 = 0; a2 < t3.byteLength; ) {
                  var n2 = a2, r2 = (t3[a2], (120 & t3[a2]) >> 3), o2 = !!(4 & t3[a2]), s2 = !!(2 & t3[a2]);
                  t3[a2], a2 += 1, o2 && (a2 += 1);
                  var d2 = Number.POSITIVE_INFINITY;
                  if (s2) {
                    d2 = 0;
                    for (var _2 = 0; ; _2++) {
                      var l2 = t3[a2++];
                      if (d2 |= (127 & l2) << 7 * _2, !(128 & l2)) break;
                    }
                  }
                  console.log(r2), 1 === r2 ? i3 = A(A({}, e3.parseSeuqneceHeader(t3.subarray(a2, a2 + d2))), { sequence_header_data: t3.subarray(n2, a2 + d2) }) : (3 == r2 && i3 || 6 == r2 && i3) && (i3 = e3.parseOBUFrameHeader(t3.subarray(a2, a2 + d2), 0, 0, i3)), a2 += d2;
                }
                return i3;
              }, e3.parseSeuqneceHeader = function(t3) {
                var i3 = new f(t3), a2 = i3.readBits(3), n2 = (i3.readBool(), i3.readBool()), r2 = true, o2 = 0, s2 = 1, d2 = void 0, _2 = [];
                if (n2) _2.push({ operating_point_idc: 0, level: i3.readBits(5), tier: 0 });
                else {
                  if (i3.readBool()) {
                    var l2 = i3.readBits(32), u2 = i3.readBits(32), c2 = i3.readBool();
                    if (c2) {
                      for (var h2 = 0; 0 === i3.readBits(1); ) h2 += 1;
                      h2 >= 32 || i3.readBits(h2);
                    }
                    o2 = u2, s2 = l2, r2 = c2, i3.readBool() && (i3.readBits(5), i3.readBits(32), d2 = i3.readBits(5), i3.readBits(5));
                  }
                  for (var p2 = i3.readBool(), m2 = i3.readBits(5), g2 = 0; g2 <= m2; g2++) {
                    var v2 = i3.readBits(12), y2 = i3.readBits(5), S2 = y2 > 7 ? i3.readBits(1) : 0;
                    _2.push({ operating_point_idc: v2, level: y2, tier: S2 }), p2 && i3.readBool() && i3.readBits(4);
                  }
                }
                var E2 = _2[0], A2 = E2.level, b2 = E2.tier, R2 = i3.readBits(4), T2 = i3.readBits(4), L2 = i3.readBits(R2 + 1) + 1, k2 = i3.readBits(T2 + 1) + 1, D2 = false;
                n2 || (D2 = i3.readBool()), D2 && (i3.readBits(4), i3.readBits(4)), i3.readBool(), i3.readBool(), i3.readBool();
                var M2 = false, C2 = 2, w2 = 2, I2 = 0;
                n2 || (i3.readBool(), i3.readBool(), i3.readBool(), i3.readBool(), (M2 = i3.readBool()) && (i3.readBool(), i3.readBool()), w2 = (C2 = i3.readBool() ? 2 : i3.readBits(1)) ? i3.readBool() ? 2 : i3.readBits(1) : 2, I2 = M2 ? i3.readBits(3) + 1 : 0);
                var B2 = i3.readBool(), O2 = (i3.readBool(), i3.readBool(), i3.readBool()), P2 = 8;
                P2 = 2 === a2 && O2 ? i3.readBool() ? 12 : 10 : O2 ? 10 : 8;
                var x2 = false;
                1 !== a2 && (x2 = i3.readBool()), i3.readBool() && (i3.readBits(8), i3.readBits(8), i3.readBits(8));
                var U2 = 1, N2 = 1;
                return x2 ? (i3.readBits(1), U2 = 1, N2 = 1) : (i3.readBits(1), 0 == a2 ? (U2 = 1, N2 = 1) : 1 == a2 ? (U2 = 0, N2 = 0) : 12 == P2 ? i3.readBits(1) && i3.readBits(1) : (U2 = 1, N2 = 0), U2 && N2 && i3.readBits(2), i3.readBits(1)), i3.readBool(), i3.destroy(), i3 = null, { codec_mimetype: "av01.".concat(a2, ".").concat(e3.getLevelString(A2, b2), ".").concat(P2.toString(10).padStart(2, "0")), level: A2, tier: b2, level_string: e3.getLevelString(A2, b2), profile_idc: a2, profile_string: "".concat(a2), bit_depth: P2, ref_frames: 1, chroma_format: e3.getChromaFormat(x2, U2, N2), chroma_format_string: e3.getChromaFormatString(x2, U2, N2), sequence_header: { frame_id_numbers_present_flag: D2, additional_frame_id_length_minus_1: void 0, delta_frame_id_length_minus_2: void 0, reduced_still_picture_header: n2, decoder_model_info_present_flag: false, operating_points: _2, buffer_removal_time_length_minus_1: d2, equal_picture_interval: r2, seq_force_screen_content_tools: C2, seq_force_integer_mv: w2, enable_order_hint: M2, order_hint_bits: I2, enable_superres: B2, frame_width_bit: R2 + 1, frame_height_bit: T2 + 1, max_frame_width: L2, max_frame_height: k2 }, keyframe: void 0, frame_rate: { fixed: r2, fps: o2 / s2, fps_den: s2, fps_num: o2 } };
              }, e3.parseOBUFrameHeader = function(t3, i3, a2, n2) {
                var r2 = n2.sequence_header, o2 = new f(t3), s2 = (r2.max_frame_width, r2.max_frame_height, 0);
                r2.frame_id_numbers_present_flag && (s2 = r2.additional_frame_id_length_minus_1 + r2.delta_frame_id_length_minus_2 + 3);
                var d2 = 0, _2 = true, l2 = true, u2 = false;
                if (!r2.reduced_still_picture_header) {
                  if (o2.readBool()) return n2;
                  _2 = 2 === (d2 = o2.readBits(2)) || 0 === d2, (l2 = o2.readBool()) && r2.decoder_model_info_present_flag && r2.equal_picture_interval, l2 && o2.readBool(), u2 = !!(3 === d2 || 0 === d2 && l2) || o2.readBool();
                }
                n2.keyframe = _2, o2.readBool();
                var c2 = r2.seq_force_screen_content_tools;
                2 === r2.seq_force_screen_content_tools && (c2 = o2.readBits(1)), c2 && (r2.seq_force_integer_mv, 2 == r2.seq_force_integer_mv && o2.readBits(1)), r2.frame_id_numbers_present_flag && o2.readBits(s2);
                var h2;
                if (h2 = 3 == d2 || !r2.reduced_still_picture_header && o2.readBool(), o2.readBits(r2.order_hint_bits), _2 || u2 || o2.readBits(3), r2.decoder_model_info_present_flag && o2.readBool()) {
                  for (var p2 = 0; p2 <= r2.operating_points_cnt_minus_1; p2++) if (r2.operating_points[p2].decoder_model_present_for_this_op[p2]) {
                    var m2 = r2.operating_points[p2].operating_point_idc;
                    (0 === m2 || m2 >> i3 & 1 && m2 >> a2 + 8 & 1) && o2.readBits(r2.buffer_removal_time_length_minus_1 + 1);
                  }
                }
                var g2 = 255;
                if (3 === d2 || 0 == d2 && l2 || (g2 = o2.readBits(8)), (_2 || 255 !== g2) && u2 && r2.enable_order_hint) for (var v2 = 0; v2 < 8; v2++) o2.readBits(r2.order_hint_bits);
                if (_2) {
                  var y2 = e3.frameSizeAndRenderSize(o2, h2, r2);
                  n2.codec_size = { width: y2.FrameWidth, height: y2.FrameHeight }, n2.present_size = { width: y2.RenderWidth, height: y2.RenderHeight }, n2.sar_ratio = { width: y2.RenderWidth / y2.FrameWidth, height: y2.RenderHeight / y2.FrameHeight };
                }
                return o2.destroy(), o2 = null, n2;
              }, e3.frameSizeAndRenderSize = function(e4, t3, i3) {
                var a2 = i3.max_frame_width, n2 = i3.max_frame_height;
                t3 && (a2 = e4.readBits(i3.frame_width_bit) + 1, n2 = e4.readBits(i3.frame_height_bit) + 1);
                var r2 = false;
                i3.enable_superres && (r2 = e4.readBool());
                var o2 = 8;
                r2 && (o2 = e4.readBits(3) + 9);
                var s2 = a2;
                a2 = Math.floor((8 * s2 + o2 / 2) / o2);
                var d2 = s2, _2 = n2;
                if (e4.readBool()) {
                  var l2 = e4.readBits(16) + 1, u2 = e4.readBits(16) + 1;
                  d2 = e4.readBits(l2) + 1, _2 = e4.readBits(u2) + 1;
                }
                return { UpscaledWidth: s2, FrameWidth: a2, FrameHeight: n2, RenderWidth: d2, RenderHeight: _2 };
              }, e3.getLevelString = function(e4, t3) {
                return "".concat(e4.toString(10).padStart(2, "0")).concat(0 === t3 ? "M" : "H");
              }, e3.getChromaFormat = function(e4, t3, i3) {
                return e4 ? 0 : 0 === t3 && 0 === i3 ? 3 : 1 === t3 && 0 === i3 ? 2 : 1 === t3 && 1 === i3 ? 1 : Number.NaN;
              }, e3.getChromaFormatString = function(e4, t3, i3) {
                return e4 ? "4:0:0" : 0 === t3 && 0 === i3 ? "4:4:4" : 1 === t3 && 0 === i3 ? "4:2:2" : 1 === t3 && 1 === i3 ? "4:2:0" : "Unknown";
              }, e3;
            })(), R = function() {
            };
            function T(e3, t3, i3) {
              if (!e3 || e3.byteLength < 2) return null;
              var a2 = 1;
              "h265" === i3 && (a2 = 2);
              var n2 = (function(e4) {
                for (var t4 = e4, i4 = t4.byteLength, a3 = new Uint8Array(i4), n3 = 0, r3 = 0; r3 < i4; r3++) r3 >= 2 && 3 === t4[r3] && 0 === t4[r3 - 1] && 0 === t4[r3 - 2] || (a3[n3] = t4[r3], n3++);
                return new Uint8Array(a3.buffer, 0, n3);
              })(e3.subarray(a2)), r2 = 0;
              if (r2 === n2.byteLength - 1 && 128 === n2[r2]) return null;
              for (var o2 = 0; r2 < n2.byteLength && 255 === n2[r2]; ) o2 += 255, r2++;
              if (r2 >= n2.byteLength) return null;
              o2 += n2[r2++];
              for (var s2 = 0; r2 < n2.byteLength && 255 === n2[r2]; ) s2 += 255, r2++;
              if (r2 >= n2.byteLength) return null;
              if (s2 += n2[r2++], r2 + s2 > n2.byteLength) return null;
              var d2 = new R();
              d2.type = o2, d2.size = s2;
              var _2 = n2.subarray(r2, r2 + s2);
              return 5 === o2 && s2 >= 16 && (d2.uuid = _2.subarray(0, 16), d2.user_data = _2.subarray(16)), void 0 !== t3 && (d2.pts = t3), d2;
            }
            var L, k = (function() {
              function e3(e4, t3) {
                this.TAG = "FLVDemuxer", this._config = t3, this._onError = null, this._onMediaInfo = null, this._onMetaDataArrived = null, this._onScriptDataArrived = null, this._onTrackMetadata = null, this._onDataAvailable = null, this._onSeiArrived = null, this._dataOffset = e4.dataOffset, this._firstParse = true, this._dispatch = false, this._hasAudio = e4.hasAudioTrack, this._hasVideo = e4.hasVideoTrack, this._hasAudioFlagOverrided = false, this._hasVideoFlagOverrided = false, this._audioInitialMetadataDispatched = false, this._videoInitialMetadataDispatched = false, this._mediaInfo = new s.default(), this._mediaInfo.hasAudio = this._hasAudio, this._mediaInfo.hasVideo = this._hasVideo, this._metadata = null, this._audioMetadata = null, this._videoMetadata = null, this._naluLengthSize = 4, this._timestampBase = 0, this._timescale = 1e3, this._duration = 0, this._durationOverrided = false, this._referenceFrameRate = { fixed: true, fps: 23.976, fps_num: 23976, fps_den: 1e3 }, this._flvSoundRateTable = [5500, 11025, 22050, 44100, 48e3], this._mpegSamplingRates = [96e3, 88200, 64e3, 48e3, 44100, 32e3, 24e3, 22050, 16e3, 12e3, 11025, 8e3, 7350], this._mpegAudioV10SampleRateTable = [44100, 48e3, 32e3, 0], this._mpegAudioV20SampleRateTable = [22050, 24e3, 16e3, 0], this._mpegAudioV25SampleRateTable = [11025, 12e3, 8e3, 0], this._mpegAudioL1BitRateTable = [0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448, -1], this._mpegAudioL2BitRateTable = [0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, -1], this._mpegAudioL3BitRateTable = [0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, -1], this._videoTrack = { type: "video", id: 1, sequenceNumber: 0, samples: [], length: 0 }, this._audioTrack = { type: "audio", id: 2, sequenceNumber: 0, samples: [], length: 0 }, this._littleEndian = (function() {
                  var e5 = new ArrayBuffer(2);
                  return new DataView(e5).setInt16(0, 256, true), 256 === new Int16Array(e5)[0];
                })();
              }
              return e3.prototype.destroy = function() {
                this._mediaInfo = null, this._metadata = null, this._audioMetadata = null, this._videoMetadata = null, this._videoTrack = null, this._audioTrack = null, this._onError = null, this._onMediaInfo = null, this._onMetaDataArrived = null, this._onScriptDataArrived = null, this._onTrackMetadata = null, this._onDataAvailable = null, this._onSeiArrived = null;
              }, e3.probe = function(e4) {
                var t3 = new Uint8Array(e4);
                if (t3.byteLength < 9) return { needMoreData: true };
                var i3 = { match: false };
                if (70 !== t3[0] || 76 !== t3[1] || 86 !== t3[2] || 1 !== t3[3]) return i3;
                var a2, n2 = (4 & t3[4]) >>> 2 != 0, r2 = !!(1 & t3[4]), o2 = (a2 = t3)[5] << 24 | a2[6] << 16 | a2[7] << 8 | a2[8];
                return o2 < 9 ? i3 : { match: true, consumed: o2, dataOffset: o2, hasAudioTrack: n2, hasVideoTrack: r2 };
              }, e3.prototype.bindDataSource = function(e4) {
                return e4.onDataArrival = this.parseChunks.bind(this), this;
              }, Object.defineProperty(e3.prototype, "onTrackMetadata", { get: function() {
                return this._onTrackMetadata;
              }, set: function(e4) {
                this._onTrackMetadata = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "onMediaInfo", { get: function() {
                return this._onMediaInfo;
              }, set: function(e4) {
                this._onMediaInfo = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "onMetaDataArrived", { get: function() {
                return this._onMetaDataArrived;
              }, set: function(e4) {
                this._onMetaDataArrived = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "onScriptDataArrived", { get: function() {
                return this._onScriptDataArrived;
              }, set: function(e4) {
                this._onScriptDataArrived = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "onSeiArrived", { get: function() {
                return this._onSeiArrived;
              }, set: function(e4) {
                this._onSeiArrived = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "onError", { get: function() {
                return this._onError;
              }, set: function(e4) {
                this._onError = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "onDataAvailable", { get: function() {
                return this._onDataAvailable;
              }, set: function(e4) {
                this._onDataAvailable = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "timestampBase", { get: function() {
                return this._timestampBase;
              }, set: function(e4) {
                this._timestampBase = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "overridedDuration", { get: function() {
                return this._duration;
              }, set: function(e4) {
                this._durationOverrided = true, this._duration = e4, this._mediaInfo.duration = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "overridedHasAudio", { set: function(e4) {
                this._hasAudioFlagOverrided = true, this._hasAudio = e4, this._mediaInfo.hasAudio = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "overridedHasVideo", { set: function(e4) {
                this._hasVideoFlagOverrided = true, this._hasVideo = e4, this._mediaInfo.hasVideo = e4;
              }, enumerable: false, configurable: true }), e3.prototype.resetMediaInfo = function() {
                this._mediaInfo = new s.default();
              }, e3.prototype._isInitialMetadataDispatched = function() {
                return this._hasAudio && this._hasVideo ? this._audioInitialMetadataDispatched && this._videoInitialMetadataDispatched : this._hasAudio && !this._hasVideo ? this._audioInitialMetadataDispatched : !(this._hasAudio || !this._hasVideo) && this._videoInitialMetadataDispatched;
              }, e3.prototype.parseChunks = function(t3, i3) {
                if (!(this._onError && this._onMediaInfo && this._onTrackMetadata && this._onDataAvailable)) throw new u.IllegalStateException("Flv: onError & onMediaInfo & onTrackMetadata & onDataAvailable callback must be specified");
                var a2 = 0, n2 = this._littleEndian;
                if (0 === i3) {
                  if (!(t3.byteLength > 13)) return 0;
                  a2 = e3.probe(t3).dataOffset;
                }
                for (this._firstParse && (this._firstParse = false, i3 + a2 !== this._dataOffset && r.default.w(this.TAG, "First time parsing but chunk byteStart invalid!"), 0 !== (o2 = new DataView(t3, a2)).getUint32(0, !n2) && r.default.w(this.TAG, "PrevTagSize0 !== 0 !!!"), a2 += 4); a2 < t3.byteLength; ) {
                  this._dispatch = true;
                  var o2 = new DataView(t3, a2);
                  if (a2 + 11 + 4 > t3.byteLength) break;
                  var s2 = o2.getUint8(0), d2 = 16777215 & o2.getUint32(0, !n2);
                  if (a2 + 11 + d2 + 4 > t3.byteLength) break;
                  if (8 === s2 || 9 === s2 || 18 === s2) {
                    var _2 = o2.getUint8(4), l2 = o2.getUint8(5), c2 = o2.getUint8(6) | l2 << 8 | _2 << 16 | o2.getUint8(7) << 24;
                    16777215 & o2.getUint32(7, !n2) && r.default.w(this.TAG, "Meet tag which has StreamID != 0!");
                    var h2 = a2 + 11;
                    switch (s2) {
                      case 8:
                        this._parseAudioData(t3, h2, d2, c2);
                        break;
                      case 9:
                        this._parseVideoData(t3, h2, d2, c2, i3 + a2);
                        break;
                      case 18:
                        this._parseScriptData(t3, h2, d2);
                    }
                    var f2 = o2.getUint32(11 + d2, !n2);
                    f2 !== 11 + d2 && r.default.w(this.TAG, "Invalid PrevTagSize ".concat(f2)), a2 += 11 + d2 + 4;
                  } else r.default.w(this.TAG, "Unsupported tag type ".concat(s2, ", skipped")), a2 += 11 + d2 + 4;
                }
                return this._isInitialMetadataDispatched() && this._dispatch && (this._audioTrack.length || this._videoTrack.length) && this._onDataAvailable(this._audioTrack, this._videoTrack), a2;
              }, e3.prototype._parseScriptData = function(e4, t3, i3) {
                var a2 = h.parseScriptData(e4, t3, i3);
                if (a2.hasOwnProperty("onMetaData")) {
                  if (null == a2.onMetaData || "object" != typeof a2.onMetaData) return void r.default.w(this.TAG, "Invalid onMetaData structure!");
                  this._metadata && r.default.w(this.TAG, "Found another onMetaData tag!"), this._metadata = a2;
                  var n2 = this._metadata.onMetaData;
                  if (this._onMetaDataArrived && this._onMetaDataArrived(Object.assign({}, n2)), "boolean" == typeof n2.hasAudio && false === this._hasAudioFlagOverrided && (this._hasAudio = n2.hasAudio, this._mediaInfo.hasAudio = this._hasAudio), "boolean" == typeof n2.hasVideo && false === this._hasVideoFlagOverrided && (this._hasVideo = n2.hasVideo, this._mediaInfo.hasVideo = this._hasVideo), "number" == typeof n2.audiodatarate && (this._mediaInfo.audioDataRate = n2.audiodatarate), "number" == typeof n2.videodatarate && (this._mediaInfo.videoDataRate = n2.videodatarate), "number" == typeof n2.width && (this._mediaInfo.width = n2.width), "number" == typeof n2.height && (this._mediaInfo.height = n2.height), "number" == typeof n2.duration) {
                    if (!this._durationOverrided) {
                      var o2 = Math.floor(n2.duration * this._timescale);
                      this._duration = o2, this._mediaInfo.duration = o2;
                    }
                  } else this._mediaInfo.duration = 0;
                  if ("number" == typeof n2.framerate) {
                    var s2 = Math.floor(1e3 * n2.framerate);
                    if (s2 > 0) {
                      var d2 = s2 / 1e3;
                      this._referenceFrameRate.fixed = true, this._referenceFrameRate.fps = d2, this._referenceFrameRate.fps_num = s2, this._referenceFrameRate.fps_den = 1e3, this._mediaInfo.fps = d2;
                    }
                  }
                  if ("object" == typeof n2.keyframes) {
                    this._mediaInfo.hasKeyframesIndex = true;
                    var _2 = n2.keyframes;
                    this._mediaInfo.keyframesIndex = this._parseKeyframesIndex(_2), n2.keyframes = null;
                  } else this._mediaInfo.hasKeyframesIndex = false;
                  this._dispatch = false, this._mediaInfo.metadata = n2, r.default.v(this.TAG, "Parsed onMetaData"), this._mediaInfo.isComplete() && this._onMediaInfo(this._mediaInfo);
                }
                Object.keys(a2).length > 0 && this._onScriptDataArrived && this._onScriptDataArrived(Object.assign({}, a2));
              }, e3.prototype._parseSEIPayload = function(e4, t3, i3) {
                var a2 = T(e4, t3, i3);
                a2 && "function" == typeof this._onSeiArrived && this._onSeiArrived(a2);
              }, e3.prototype._parseKeyframesIndex = function(e4) {
                for (var t3 = [], i3 = [], a2 = 1; a2 < e4.times.length; a2++) {
                  var n2 = this._timestampBase + Math.floor(1e3 * e4.times[a2]);
                  t3.push(n2), i3.push(e4.filepositions[a2]);
                }
                return { times: t3, filepositions: i3 };
              }, e3.prototype._parseAudioData = function(e4, t3, i3, a2) {
                if (i3 <= 1) r.default.w(this.TAG, "Flv: Invalid audio packet, missing SoundData payload!");
                else if (true !== this._hasAudioFlagOverrided || false !== this._hasAudio) {
                  this._littleEndian;
                  var n2 = new DataView(e4, t3, i3).getUint8(0), o2 = n2 >>> 4;
                  if (9 !== o2) if (2 === o2 || 3 === o2 || 10 === o2) {
                    var s2 = 0, d2 = (12 & n2) >>> 2;
                    if (d2 >= 0 && d2 <= 4) {
                      s2 = this._flvSoundRateTable[d2];
                      var _2 = (2 & n2) >>> 1, l2 = 1 & n2, u2 = this._audioMetadata, c2 = this._audioTrack;
                      if (u2 || (false === this._hasAudio && false === this._hasAudioFlagOverrided && (this._hasAudio = true, this._mediaInfo.hasAudio = true), (u2 = this._audioMetadata = {}).type = "audio", u2.id = c2.id, u2.timescale = this._timescale, u2.duration = this._duration, u2.audioSampleRate = s2, u2.channelCount = 0 === l2 ? 1 : 2), 10 === o2) {
                        var h2 = this._parseAACAudioData(e4, t3 + 1, i3 - 1);
                        if (null == h2) return;
                        if (0 === h2.packetType) {
                          if (u2.config) {
                            if (E(h2.data.config, u2.config)) return;
                            r.default.w(this.TAG, "AudioSpecificConfig has been changed, re-generate initialization segment");
                          }
                          var f2 = h2.data;
                          u2.audioSampleRate = f2.samplingRate, u2.channelCount = f2.channelCount, u2.codec = f2.codec, u2.originalCodec = f2.originalCodec, u2.config = f2.config, u2.refSampleDuration = 1024 / u2.audioSampleRate * u2.timescale, r.default.v(this.TAG, "Parsed AudioSpecificConfig"), this._isInitialMetadataDispatched() ? this._dispatch && (this._audioTrack.length || this._videoTrack.length) && this._onDataAvailable(this._audioTrack, this._videoTrack) : this._audioInitialMetadataDispatched = true, this._dispatch = false, this._onTrackMetadata("audio", u2), (y2 = this._mediaInfo).audioCodec = u2.originalCodec, y2.audioSampleRate = u2.audioSampleRate, y2.audioChannelCount = u2.channelCount, y2.hasVideo ? null != y2.videoCodec && (y2.mimeType = 'video/x-flv; codecs="' + y2.videoCodec + "," + y2.audioCodec + '"') : y2.mimeType = 'video/x-flv; codecs="' + y2.audioCodec + '"', y2.isComplete() && this._onMediaInfo(y2);
                        } else if (1 === h2.packetType) {
                          var p2 = this._timestampBase + a2, g2 = { unit: h2.data, length: h2.data.byteLength, dts: p2, pts: p2 };
                          c2.samples.push(g2), c2.length += h2.data.length;
                        } else r.default.e(this.TAG, "Flv: Unsupported AAC data type ".concat(h2.packetType));
                      } else if (2 === o2) {
                        if (!u2.codec) {
                          if (null == (f2 = this._parseMP3AudioData(e4, t3 + 1, i3 - 1, true))) return;
                          u2.audioSampleRate = f2.samplingRate, u2.channelCount = f2.channelCount, u2.codec = f2.codec, u2.originalCodec = f2.originalCodec, u2.refSampleDuration = 1152 / u2.audioSampleRate * u2.timescale, r.default.v(this.TAG, "Parsed MPEG Audio Frame Header"), this._audioInitialMetadataDispatched = true, this._onTrackMetadata("audio", u2), (y2 = this._mediaInfo).audioCodec = u2.codec, y2.audioSampleRate = u2.audioSampleRate, y2.audioChannelCount = u2.channelCount, y2.audioDataRate = f2.bitRate, y2.hasVideo ? null != y2.videoCodec && (y2.mimeType = 'video/x-flv; codecs="' + y2.videoCodec + "," + y2.audioCodec + '"') : y2.mimeType = 'video/x-flv; codecs="' + y2.audioCodec + '"', y2.isComplete() && this._onMediaInfo(y2);
                        }
                        if (null == (S2 = this._parseMP3AudioData(e4, t3 + 1, i3 - 1, false))) return;
                        p2 = this._timestampBase + a2;
                        var v2 = { unit: S2, length: S2.byteLength, dts: p2, pts: p2 };
                        c2.samples.push(v2), c2.length += S2.length;
                      } else if (3 === o2) {
                        var y2;
                        u2.codec || (u2.audioSampleRate = s2, u2.sampleSize = 8 * (_2 + 1), u2.littleEndian = true, u2.codec = "ipcm", u2.originalCodec = "ipcm", this._audioInitialMetadataDispatched = true, this._onTrackMetadata("audio", u2), (y2 = this._mediaInfo).audioCodec = u2.codec, y2.audioSampleRate = u2.audioSampleRate, y2.audioChannelCount = u2.channelCount, y2.audioDataRate = u2.sampleSize * u2.audioSampleRate, y2.hasVideo ? null != y2.videoCodec && (y2.mimeType = 'video/x-flv; codecs="' + y2.videoCodec + "," + y2.audioCodec + '"') : y2.mimeType = 'video/x-flv; codecs="' + y2.audioCodec + '"', y2.isComplete() && this._onMediaInfo(y2));
                        var S2 = new Uint8Array(e4, t3 + 1, i3 - 1), A2 = (p2 = this._timestampBase + a2, { unit: S2, length: S2.byteLength, dts: p2, pts: p2 });
                        c2.samples.push(A2), c2.length += S2.length;
                      }
                    } else this._onError(m.default.FORMAT_ERROR, "Flv: Invalid audio sample rate idx: " + d2);
                  } else this._onError(m.default.CODEC_UNSUPPORTED, "Flv: Unsupported audio codec idx: " + o2);
                  else {
                    if (i3 <= 5) return void r.default.w(this.TAG, "Flv: Invalid audio packet, missing AudioFourCC in Ehnanced FLV payload!");
                    var b2 = 15 & n2, R2 = String.fromCharCode.apply(String, new Uint8Array(e4, t3, i3).slice(1, 5));
                    switch (R2) {
                      case "Opus":
                        this._parseOpusAudioPacket(e4, t3 + 5, i3 - 5, a2, b2);
                        break;
                      case "fLaC":
                        this._parseFlacAudioPacket(e4, t3 + 5, i3 - 5, a2, b2);
                        break;
                      default:
                        this._onError(m.default.CODEC_UNSUPPORTED, "Flv: Unsupported audio codec: " + R2);
                    }
                  }
                }
              }, e3.prototype._parseAACAudioData = function(e4, t3, i3) {
                if (!(i3 <= 1)) {
                  var a2 = {}, n2 = new Uint8Array(e4, t3, i3);
                  return a2.packetType = n2[0], 0 === n2[0] ? a2.data = this._parseAACAudioSpecificConfig(e4, t3 + 1, i3 - 1) : a2.data = n2.subarray(1), a2;
                }
                r.default.w(this.TAG, "Flv: Invalid AAC packet, missing AACPacketType or/and Data!");
              }, e3.prototype._parseAACAudioSpecificConfig = function(e4, t3, i3) {
                var a2, n2, r2 = new Uint8Array(e4, t3, i3), o2 = null, s2 = 0, d2 = null;
                if (s2 = a2 = r2[0] >>> 3, (n2 = (7 & r2[0]) << 1 | r2[1] >>> 7) < 0 || n2 >= this._mpegSamplingRates.length) this._onError(m.default.FORMAT_ERROR, "Flv: AAC invalid sampling frequency index!");
                else {
                  var _2 = this._mpegSamplingRates[n2], l2 = (120 & r2[1]) >>> 3;
                  if (!(l2 < 0 || l2 >= 8)) {
                    5 === s2 && (d2 = (7 & r2[1]) << 1 | r2[2] >>> 7, r2[2]);
                    var u2 = self.navigator.userAgent.toLowerCase();
                    return -1 !== u2.indexOf("firefox") ? n2 >= 6 ? (s2 = 5, o2 = new Array(4), d2 = n2 - 3) : (s2 = 2, o2 = new Array(2), d2 = n2) : -1 !== u2.indexOf("android") ? (s2 = 2, o2 = new Array(2), d2 = n2) : (s2 = 5, d2 = n2, o2 = new Array(4), n2 >= 6 ? d2 = n2 - 3 : 1 === l2 && (s2 = 2, o2 = new Array(2), d2 = n2)), o2[0] = s2 << 3, o2[0] |= (15 & n2) >>> 1, o2[1] = (15 & n2) << 7, o2[1] |= (15 & l2) << 3, 5 === s2 && (o2[1] |= (15 & d2) >>> 1, o2[2] = (1 & d2) << 7, o2[2] |= 8, o2[3] = 0), { config: o2, samplingRate: _2, channelCount: l2, codec: "mp4a.40." + s2, originalCodec: "mp4a.40." + a2 };
                  }
                  this._onError(m.default.FORMAT_ERROR, "Flv: AAC invalid channel configuration");
                }
              }, e3.prototype._parseMP3AudioData = function(e4, t3, i3, a2) {
                if (!(i3 < 4)) {
                  this._littleEndian;
                  var n2 = new Uint8Array(e4, t3, i3), o2 = null;
                  if (a2) {
                    if (255 !== n2[0]) return;
                    var s2 = n2[1] >>> 3 & 3, d2 = (6 & n2[1]) >> 1, _2 = (240 & n2[2]) >>> 4, l2 = (12 & n2[2]) >>> 2, u2 = 3 & ~(n2[3] >>> 6) ? 2 : 1, c2 = 0, h2 = 0;
                    switch (s2) {
                      case 0:
                        c2 = this._mpegAudioV25SampleRateTable[l2];
                        break;
                      case 2:
                        c2 = this._mpegAudioV20SampleRateTable[l2];
                        break;
                      case 3:
                        c2 = this._mpegAudioV10SampleRateTable[l2];
                    }
                    switch (d2) {
                      case 1:
                        _2 < this._mpegAudioL3BitRateTable.length && (h2 = this._mpegAudioL3BitRateTable[_2]);
                        break;
                      case 2:
                        _2 < this._mpegAudioL2BitRateTable.length && (h2 = this._mpegAudioL2BitRateTable[_2]);
                        break;
                      case 3:
                        _2 < this._mpegAudioL1BitRateTable.length && (h2 = this._mpegAudioL1BitRateTable[_2]);
                    }
                    o2 = { bitRate: h2, samplingRate: c2, channelCount: u2, codec: "mp3", originalCodec: "mp3" };
                  } else o2 = n2;
                  return o2;
                }
                r.default.w(this.TAG, "Flv: Invalid MP3 packet, header missing!");
              }, e3.prototype._parseOpusAudioPacket = function(e4, t3, i3, a2, n2) {
                if (0 === n2) this._parseOpusSequenceHeader(e4, t3, i3);
                else if (1 === n2) this._parseOpusAudioData(e4, t3, i3, a2);
                else if (2 !== n2) return void this._onError(m.default.FORMAT_ERROR, "Flv: Invalid video packet type ".concat(n2));
              }, e3.prototype._parseOpusSequenceHeader = function(e4, t3, i3) {
                if (i3 <= 16) r.default.w(this.TAG, "Flv: Invalid OpusSequenceHeader, lack of data!");
                else {
                  var a2 = this._audioMetadata, n2 = this._audioTrack;
                  a2 || (false === this._hasAudio && false === this._hasAudioFlagOverrided && (this._hasAudio = true, this._mediaInfo.hasAudio = true), (a2 = this._audioMetadata = {}).type = "audio", a2.id = n2.id, a2.timescale = this._timescale, a2.duration = this._duration);
                  var o2 = new DataView(e4, t3, i3);
                  o2.setUint8(8, 0);
                  var s2 = o2.getUint8(9);
                  o2.setUint16(10, o2.getUint16(10, true), false);
                  var d2 = o2.getUint32(12, true);
                  o2.setUint32(12, o2.getUint32(12, true), false);
                  var _2 = { config: new Uint8Array(e4, t3 + 8, i3 - 8), channelCount: s2, samplingFrequence: d2, codec: "opus", originalCodec: "opus" };
                  if (a2.config) {
                    if (E(_2.config, a2.config)) return;
                    r.default.w(this.TAG, "OpusSequenceHeader has been changed, re-generate initialization segment");
                  }
                  a2.audioSampleRate = _2.samplingFrequence, a2.channelCount = _2.channelCount, a2.codec = _2.codec, a2.originalCodec = _2.originalCodec, a2.config = _2.config, a2.refSampleDuration = 20, r.default.v(this.TAG, "Parsed OpusSequenceHeader"), this._isInitialMetadataDispatched() ? this._dispatch && (this._audioTrack.length || this._videoTrack.length) && this._onDataAvailable(this._audioTrack, this._videoTrack) : this._audioInitialMetadataDispatched = true, this._dispatch = false, this._onTrackMetadata("audio", a2);
                  var l2 = this._mediaInfo;
                  l2.audioCodec = a2.originalCodec, l2.audioSampleRate = a2.audioSampleRate, l2.audioChannelCount = a2.channelCount, l2.hasVideo ? null != l2.videoCodec && (l2.mimeType = 'video/x-flv; codecs="' + l2.videoCodec + "," + l2.audioCodec + '"') : l2.mimeType = 'video/x-flv; codecs="' + l2.audioCodec + '"', l2.isComplete() && this._onMediaInfo(l2);
                }
              }, e3.prototype._parseOpusAudioData = function(e4, t3, i3, a2) {
                var n2 = this._audioTrack, r2 = new Uint8Array(e4, t3, i3), o2 = this._timestampBase + a2, s2 = { unit: r2, length: r2.byteLength, dts: o2, pts: o2 };
                n2.samples.push(s2), n2.length += r2.length;
              }, e3.prototype._parseFlacAudioPacket = function(e4, t3, i3, a2, n2) {
                if (0 === n2) this._parseFlacSequenceHeader(e4, t3, i3);
                else if (1 === n2) this._parseFlacAudioData(e4, t3, i3, a2);
                else if (2 !== n2) return void this._onError(m.default.FORMAT_ERROR, "Flv: Invalid Flac audio packet type ".concat(n2));
              }, e3.prototype._parseFlacSequenceHeader = function(e4, t3, i3) {
                var a2 = this._audioMetadata, n2 = this._audioTrack;
                a2 || (false === this._hasAudio && false === this._hasAudioFlagOverrided && (this._hasAudio = true, this._mediaInfo.hasAudio = true), (a2 = this._audioMetadata = {}).type = "audio", a2.id = n2.id, a2.timescale = this._timescale, a2.duration = this._duration);
                var o2 = new Uint8Array(e4, t3 + 4, i3 - 4), s2 = new f(o2), d2 = s2.readBits(16), _2 = s2.readBits(16), l2 = _2 === d2 ? _2 : null;
                s2.readBits(24), s2.readBits(24);
                var u2 = s2.readBits(20), c2 = s2.readBits(3) + 1, h2 = s2.readBits(5) + 1;
                s2.destroy();
                var p2 = new Uint8Array(o2.byteLength + 4);
                p2.set(o2, 4), p2[0] = 128, p2[1] = o2.byteLength >>> 16 & 255, p2[2] = o2.byteLength >>> 8 & 255, p2[3] = o2.byteLength >>> 0 & 255;
                var m2 = { config: p2, channelCount: c2, samplingFrequence: u2, sampleSize: h2, codec: "flac", originalCodec: "flac" };
                if (a2.config) {
                  if (E(m2.config, a2.config)) return;
                  r.default.w(this.TAG, "FlacSequenceHeader has been changed, re-generate initialization segment");
                }
                a2.audioSampleRate = m2.samplingFrequence, a2.channelCount = m2.channelCount, a2.sampleSize = m2.sampleSize, a2.codec = m2.codec, a2.originalCodec = m2.originalCodec, a2.config = m2.config, a2.refSampleDuration = null != l2 ? 1e3 * l2 / m2.samplingFrequence : null, r.default.v(this.TAG, "Parsed FlacSequenceHeader"), this._isInitialMetadataDispatched() ? this._dispatch && (this._audioTrack.length || this._videoTrack.length) && this._onDataAvailable(this._audioTrack, this._videoTrack) : this._audioInitialMetadataDispatched = true, this._dispatch = false, this._onTrackMetadata("audio", a2);
                var g2 = this._mediaInfo;
                g2.audioCodec = a2.originalCodec, g2.audioSampleRate = a2.audioSampleRate, g2.audioChannelCount = a2.channelCount, g2.hasVideo ? null != g2.videoCodec && (g2.mimeType = 'video/x-flv; codecs="' + g2.videoCodec + "," + g2.audioCodec + '"') : g2.mimeType = 'video/x-flv; codecs="' + g2.audioCodec + '"', g2.isComplete() && this._onMediaInfo(g2);
              }, e3.prototype._parseFlacAudioData = function(e4, t3, i3, a2) {
                var n2 = this._audioTrack, r2 = new Uint8Array(e4, t3, i3), o2 = this._timestampBase + a2, s2 = { unit: r2, length: r2.byteLength, dts: o2, pts: o2 };
                n2.samples.push(s2), n2.length += r2.length;
              }, e3.prototype._parseVideoData = function(e4, t3, i3, a2, n2) {
                if (i3 <= 1) r.default.w(this.TAG, "Flv: Invalid video packet, missing VideoData payload!");
                else if (true !== this._hasVideoFlagOverrided || false !== this._hasVideo) {
                  var o2 = new Uint8Array(e4, t3, i3)[0], s2 = (112 & o2) >>> 4;
                  if (128 & o2) {
                    var d2 = 15 & o2, _2 = String.fromCharCode.apply(String, new Uint8Array(e4, t3, i3).slice(1, 5));
                    if ("hvc1" === _2) this._parseEnhancedHEVCVideoPacket(e4, t3 + 5, i3 - 5, a2, n2, s2, d2);
                    else {
                      if ("av01" !== _2) return void this._onError(m.default.CODEC_UNSUPPORTED, "Flv: Unsupported codec in video frame: ".concat(_2));
                      this._parseEnhancedAV1VideoPacket(e4, t3 + 5, i3 - 5, a2, n2, s2, d2);
                    }
                  } else {
                    var l2 = 15 & o2;
                    if (7 === l2) this._parseAVCVideoPacket(e4, t3 + 1, i3 - 1, a2, n2, s2);
                    else {
                      if (12 !== l2) return void this._onError(m.default.CODEC_UNSUPPORTED, "Flv: Unsupported codec in video frame: ".concat(l2));
                      this._parseHEVCVideoPacket(e4, t3 + 1, i3 - 1, a2, n2, s2);
                    }
                  }
                }
              }, e3.prototype._parseAVCVideoPacket = function(e4, t3, i3, a2, n2, o2) {
                if (i3 < 4) r.default.w(this.TAG, "Flv: Invalid AVC packet, missing AVCPacketType or/and CompositionTime");
                else {
                  var s2 = this._littleEndian, d2 = new DataView(e4, t3, i3), _2 = d2.getUint8(0), l2 = (16777215 & d2.getUint32(0, !s2)) << 8 >> 8;
                  if (0 === _2) this._parseAVCDecoderConfigurationRecord(e4, t3 + 4, i3 - 4);
                  else if (1 === _2) this._parseAVCVideoData(e4, t3 + 4, i3 - 4, a2, n2, o2, l2);
                  else if (2 !== _2) return void this._onError(m.default.FORMAT_ERROR, "Flv: Invalid video packet type ".concat(_2));
                }
              }, e3.prototype._parseHEVCVideoPacket = function(e4, t3, i3, a2, n2, o2) {
                if (i3 < 4) r.default.w(this.TAG, "Flv: Invalid HEVC packet, missing HEVCPacketType or/and CompositionTime");
                else {
                  var s2 = this._littleEndian, d2 = new DataView(e4, t3, i3), _2 = d2.getUint8(0), l2 = (16777215 & d2.getUint32(0, !s2)) << 8 >> 8;
                  if (0 === _2) this._parseHEVCDecoderConfigurationRecord(e4, t3 + 4, i3 - 4);
                  else if (1 === _2) this._parseHEVCVideoData(e4, t3 + 4, i3 - 4, a2, n2, o2, l2);
                  else if (2 !== _2) return void this._onError(m.default.FORMAT_ERROR, "Flv: Invalid video packet type ".concat(_2));
                }
              }, e3.prototype._parseEnhancedHEVCVideoPacket = function(e4, t3, i3, a2, n2, r2, o2) {
                var s2 = this._littleEndian, d2 = new DataView(e4, t3, i3);
                if (0 === o2) this._parseHEVCDecoderConfigurationRecord(e4, t3, i3);
                else if (1 === o2) {
                  var _2 = (4294967040 & d2.getUint32(0, !s2)) >> 8;
                  this._parseHEVCVideoData(e4, t3 + 3, i3 - 3, a2, n2, r2, _2);
                } else if (3 === o2) this._parseHEVCVideoData(e4, t3, i3, a2, n2, r2, 0);
                else if (2 !== o2) return void this._onError(m.default.FORMAT_ERROR, "Flv: Invalid video packet type ".concat(o2));
              }, e3.prototype._parseEnhancedAV1VideoPacket = function(e4, t3, i3, a2, n2, r2, o2) {
                if (this._littleEndian, new DataView(e4, t3, i3), 0 === o2) this._parseAV1CodecConfigurationRecord(e4, t3, i3);
                else if (1 === o2) this._parseAV1VideoData(e4, t3, i3, a2, n2, r2, 0);
                else {
                  if (5 === o2) return void this._onError(m.default.FORMAT_ERROR, "Flv: Not Supported MP2T AV1 video packet type ".concat(o2));
                  if (2 !== o2) return void this._onError(m.default.FORMAT_ERROR, "Flv: Invalid video packet type ".concat(o2));
                }
              }, e3.prototype._parseAVCDecoderConfigurationRecord = function(e4, t3, i3) {
                if (i3 < 7) r.default.w(this.TAG, "Flv: Invalid AVCDecoderConfigurationRecord, lack of data!");
                else {
                  var a2 = this._videoMetadata, n2 = this._videoTrack, o2 = this._littleEndian, s2 = new DataView(e4, t3, i3);
                  if (a2) {
                    if (void 0 !== a2.avcc) {
                      var d2 = new Uint8Array(e4, t3, i3);
                      if (E(d2, a2.avcc)) return;
                      r.default.w(this.TAG, "AVCDecoderConfigurationRecord has been changed, re-generate initialization segment");
                    }
                  } else false === this._hasVideo && false === this._hasVideoFlagOverrided && (this._hasVideo = true, this._mediaInfo.hasVideo = true), (a2 = this._videoMetadata = {}).type = "video", a2.id = n2.id, a2.timescale = this._timescale, a2.duration = this._duration;
                  var _2 = s2.getUint8(0), l2 = s2.getUint8(1);
                  if (s2.getUint8(2), s2.getUint8(3), 1 === _2 && 0 !== l2) if (this._naluLengthSize = 1 + (3 & s2.getUint8(4)), 3 === this._naluLengthSize || 4 === this._naluLengthSize) {
                    var u2 = 31 & s2.getUint8(5);
                    if (0 !== u2) {
                      u2 > 1 && r.default.w(this.TAG, "Flv: Strange AVCDecoderConfigurationRecord: SPS Count = ".concat(u2));
                      for (var c2 = 6, h2 = 0; h2 < u2; h2++) {
                        var f2 = s2.getUint16(c2, !o2);
                        if (c2 += 2, 0 !== f2) {
                          var g2 = new Uint8Array(e4, t3 + c2, f2);
                          c2 += f2;
                          var v2 = p.parseSPS(g2);
                          if (0 === h2) {
                            a2.codecWidth = v2.codec_size.width, a2.codecHeight = v2.codec_size.height, a2.presentWidth = v2.present_size.width, a2.presentHeight = v2.present_size.height, a2.profile = v2.profile_string, a2.level = v2.level_string, a2.bitDepth = v2.bit_depth, a2.chromaFormat = v2.chroma_format, a2.sarRatio = v2.sar_ratio, a2.frameRate = v2.frame_rate, false !== v2.frame_rate.fixed && 0 !== v2.frame_rate.fps_num && 0 !== v2.frame_rate.fps_den || (a2.frameRate = this._referenceFrameRate);
                            var y2 = a2.frameRate.fps_den, S2 = a2.frameRate.fps_num;
                            a2.refSampleDuration = a2.timescale * (y2 / S2);
                            for (var A2 = g2.subarray(1, 4), b2 = "avc1.", R2 = 0; R2 < 3; R2++) {
                              var T2 = A2[R2].toString(16);
                              T2.length < 2 && (T2 = "0" + T2), b2 += T2;
                            }
                            a2.codec = b2;
                            var L2 = this._mediaInfo;
                            L2.width = a2.codecWidth, L2.height = a2.codecHeight, L2.fps = a2.frameRate.fps, L2.profile = a2.profile, L2.level = a2.level, L2.refFrames = v2.ref_frames, L2.chromaFormat = v2.chroma_format_string, L2.sarNum = a2.sarRatio.width, L2.sarDen = a2.sarRatio.height, L2.videoCodec = b2, L2.hasAudio ? null != L2.audioCodec && (L2.mimeType = 'video/x-flv; codecs="' + L2.videoCodec + "," + L2.audioCodec + '"') : L2.mimeType = 'video/x-flv; codecs="' + L2.videoCodec + '"', L2.isComplete() && this._onMediaInfo(L2);
                          }
                        }
                      }
                      var k2 = s2.getUint8(c2);
                      if (0 !== k2) {
                        for (k2 > 1 && r.default.w(this.TAG, "Flv: Strange AVCDecoderConfigurationRecord: PPS Count = ".concat(k2)), c2++, h2 = 0; h2 < k2; h2++) f2 = s2.getUint16(c2, !o2), c2 += 2, 0 !== f2 && (c2 += f2);
                        a2.avcc = new Uint8Array(i3), a2.avcc.set(new Uint8Array(e4, t3, i3), 0), r.default.v(this.TAG, "Parsed AVCDecoderConfigurationRecord"), this._isInitialMetadataDispatched() ? this._dispatch && (this._audioTrack.length || this._videoTrack.length) && this._onDataAvailable(this._audioTrack, this._videoTrack) : this._videoInitialMetadataDispatched = true, this._dispatch = false, this._onTrackMetadata("video", a2);
                      } else this._onError(m.default.FORMAT_ERROR, "Flv: Invalid AVCDecoderConfigurationRecord: No PPS");
                    } else this._onError(m.default.FORMAT_ERROR, "Flv: Invalid AVCDecoderConfigurationRecord: No SPS");
                  } else this._onError(m.default.FORMAT_ERROR, "Flv: Strange NaluLengthSizeMinusOne: ".concat(this._naluLengthSize - 1));
                  else this._onError(m.default.FORMAT_ERROR, "Flv: Invalid AVCDecoderConfigurationRecord");
                }
              }, e3.prototype._parseHEVCDecoderConfigurationRecord = function(e4, t3, i3) {
                if (i3 < 22) r.default.w(this.TAG, "Flv: Invalid HEVCDecoderConfigurationRecord, lack of data!");
                else {
                  var a2 = this._videoMetadata, n2 = this._videoTrack, o2 = this._littleEndian, s2 = new DataView(e4, t3, i3);
                  if (a2) {
                    if (void 0 !== a2.hvcc) {
                      var d2 = new Uint8Array(e4, t3, i3);
                      if (E(d2, a2.hvcc)) return;
                      r.default.w(this.TAG, "HEVCDecoderConfigurationRecord has been changed, re-generate initialization segment");
                    }
                  } else false === this._hasVideo && false === this._hasVideoFlagOverrided && (this._hasVideo = true, this._mediaInfo.hasVideo = true), (a2 = this._videoMetadata = {}).type = "video", a2.id = n2.id, a2.timescale = this._timescale, a2.duration = this._duration;
                  var _2 = s2.getUint8(0), l2 = 31 & s2.getUint8(1);
                  if (0 !== _2 && 1 !== _2 || 0 === l2) this._onError(m.default.FORMAT_ERROR, "Flv: Invalid HEVCDecoderConfigurationRecord");
                  else if (this._naluLengthSize = 1 + (3 & s2.getUint8(21)), 3 === this._naluLengthSize || 4 === this._naluLengthSize) {
                    for (var u2 = s2.getUint8(22), c2 = 0, h2 = 23; c2 < u2; c2++) {
                      var f2 = 63 & s2.getUint8(h2 + 0), p2 = s2.getUint16(h2 + 1, !o2);
                      h2 += 3;
                      for (var v2 = 0; v2 < p2; v2++) {
                        var y2 = s2.getUint16(h2 + 0, !o2);
                        if (0 === v2) if (33 === f2) {
                          h2 += 2;
                          var S2 = new Uint8Array(e4, t3 + h2, y2), A2 = g.parseSPS(S2);
                          a2.codecWidth = A2.codec_size.width, a2.codecHeight = A2.codec_size.height, a2.presentWidth = A2.present_size.width, a2.presentHeight = A2.present_size.height, a2.profile = A2.profile_string, a2.level = A2.level_string, a2.bitDepth = A2.bit_depth, a2.chromaFormat = A2.chroma_format, a2.sarRatio = A2.sar_ratio, a2.frameRate = A2.frame_rate, false !== A2.frame_rate.fixed && 0 !== A2.frame_rate.fps_num && 0 !== A2.frame_rate.fps_den || (a2.frameRate = this._referenceFrameRate);
                          var b2 = a2.frameRate.fps_den, R2 = a2.frameRate.fps_num;
                          a2.refSampleDuration = a2.timescale * (b2 / R2), a2.codec = A2.codec_mimetype;
                          var T2 = this._mediaInfo;
                          T2.width = a2.codecWidth, T2.height = a2.codecHeight, T2.fps = a2.frameRate.fps, T2.profile = a2.profile, T2.level = a2.level, T2.refFrames = A2.ref_frames, T2.chromaFormat = A2.chroma_format_string, T2.sarNum = a2.sarRatio.width, T2.sarDen = a2.sarRatio.height, T2.videoCodec = A2.codec_mimetype, T2.hasAudio ? null != T2.audioCodec && (T2.mimeType = 'video/x-flv; codecs="' + T2.videoCodec + "," + T2.audioCodec + '"') : T2.mimeType = 'video/x-flv; codecs="' + T2.videoCodec + '"', T2.isComplete() && this._onMediaInfo(T2), h2 += y2;
                        } else h2 += 2 + y2;
                        else h2 += 2 + y2;
                      }
                    }
                    a2.hvcc = new Uint8Array(i3), a2.hvcc.set(new Uint8Array(e4, t3, i3), 0), r.default.v(this.TAG, "Parsed HEVCDecoderConfigurationRecord"), this._isInitialMetadataDispatched() ? this._dispatch && (this._audioTrack.length || this._videoTrack.length) && this._onDataAvailable(this._audioTrack, this._videoTrack) : this._videoInitialMetadataDispatched = true, this._dispatch = false, this._onTrackMetadata("video", a2);
                  } else this._onError(m.default.FORMAT_ERROR, "Flv: Strange NaluLengthSizeMinusOne: ".concat(this._naluLengthSize - 1));
                }
              }, e3.prototype._parseAV1CodecConfigurationRecord = function(e4, t3, i3) {
                if (i3 < 4) r.default.w(this.TAG, "Flv: Invalid AV1CodecConfigurationRecord, lack of data!");
                else {
                  var a2 = this._videoMetadata, n2 = this._videoTrack, o2 = (this._littleEndian, new DataView(e4, t3, i3));
                  a2 ? void 0 !== a2.av1c && r.default.w(this.TAG, "Found another AV1CodecConfigurationRecord!") : (false === this._hasVideo && false === this._hasVideoFlagOverrided && (this._hasVideo = true, this._mediaInfo.hasVideo = true), (a2 = this._videoMetadata = {}).type = "video", a2.id = n2.id, a2.timescale = this._timescale, a2.duration = this._duration);
                  var s2 = 127 & o2.getUint8(0);
                  if (o2.getUint8(1), o2.getUint8(1), o2.getUint8(2), 1 === s2) {
                    var d2 = b.parseOBUs(new Uint8Array(e4, t3 + 4, i3 - 4));
                    if (null != d2) {
                      a2.profile = d2.profile_string, a2.level = d2.level_string, a2.bitDepth = d2.bit_depth, a2.chromaFormat = d2.chroma_format, a2.frameRate = d2.frame_rate, false !== d2.frame_rate.fixed && 0 !== d2.frame_rate.fps_num && 0 !== d2.frame_rate.fps_den || (a2.frameRate = this._referenceFrameRate);
                      var _2 = a2.frameRate.fps_den, l2 = a2.frameRate.fps_num;
                      a2.refSampleDuration = a2.timescale * (_2 / l2), a2.codec = d2.codec_mimetype, a2.extra = d2;
                      var u2 = this._mediaInfo;
                      u2.fps = a2.frameRate.fps, u2.profile = a2.profile, u2.level = a2.level, u2.refFrames = d2.ref_frames, u2.chromaFormat = d2.chroma_format_string, u2.videoCodec = d2.codec_mimetype, u2.hasAudio ? null != u2.audioCodec && (u2.mimeType = 'video/x-flv; codecs="' + u2.videoCodec + "," + u2.audioCodec + '"') : u2.mimeType = 'video/x-flv; codecs="' + u2.videoCodec + '"', u2.isComplete() && this._onMediaInfo(u2), a2.av1c = new Uint8Array(i3), a2.av1c.set(new Uint8Array(e4, t3, i3), 0), r.default.v(this.TAG, "Preparing AV1CodecConfigurationRecord");
                    } else this._onError(m.default.FORMAT_ERROR, "Flv: Invalid AV1CodecConfigurationRecord");
                  } else this._onError(m.default.FORMAT_ERROR, "Flv: Invalid AV1CodecConfigurationRecord");
                }
              }, e3.prototype._parseAVCVideoData = function(e4, t3, i3, a2, n2, o2, s2) {
                for (var d2 = this._littleEndian, _2 = new DataView(e4, t3, i3), l2 = [], u2 = 0, c2 = 0, h2 = this._naluLengthSize, f2 = this._timestampBase + a2, p2 = 1 === o2; c2 < i3; ) {
                  if (c2 + 4 >= i3) {
                    r.default.w(this.TAG, "Malformed Nalu near timestamp ".concat(f2, ", offset = ").concat(c2, ", dataSize = ").concat(i3));
                    break;
                  }
                  var m2 = _2.getUint32(c2, !d2);
                  if (3 === h2 && (m2 >>>= 8), m2 > i3 - h2) return void r.default.w(this.TAG, "Malformed Nalus near timestamp ".concat(f2, ", NaluSize > DataSize!"));
                  var g2 = 31 & _2.getUint8(c2 + h2);
                  5 === g2 && (p2 = true);
                  var v2 = new Uint8Array(e4, t3 + c2, h2 + m2), y2 = { type: g2, data: v2 };
                  l2.push(y2), u2 += v2.byteLength, 6 === g2 && this._parseSEIPayload(v2.subarray(h2), f2 + s2, "h264"), c2 += h2 + m2;
                }
                if (l2.length) {
                  var S2 = this._videoTrack, E2 = { units: l2, length: u2, isKeyframe: p2, dts: f2, cts: s2, pts: f2 + s2 };
                  p2 && (E2.fileposition = n2), S2.samples.push(E2), S2.length += u2;
                }
              }, e3.prototype._parseHEVCVideoData = function(e4, t3, i3, a2, n2, o2, s2) {
                for (var d2 = this._littleEndian, _2 = new DataView(e4, t3, i3), l2 = [], u2 = 0, c2 = 0, h2 = this._naluLengthSize, f2 = this._timestampBase + a2, p2 = 1 === o2; c2 < i3; ) {
                  if (c2 + 4 >= i3) {
                    r.default.w(this.TAG, "Malformed Nalu near timestamp ".concat(f2, ", offset = ").concat(c2, ", dataSize = ").concat(i3));
                    break;
                  }
                  var m2 = _2.getUint32(c2, !d2);
                  if (3 === h2 && (m2 >>>= 8), m2 > i3 - h2) return void r.default.w(this.TAG, "Malformed Nalus near timestamp ".concat(f2, ", NaluSize > DataSize!"));
                  var g2 = _2.getUint8(c2 + h2) >> 1 & 63;
                  19 !== g2 && 20 !== g2 && 21 !== g2 || (p2 = true);
                  var v2 = new Uint8Array(e4, t3 + c2, h2 + m2), y2 = { type: g2, data: v2 };
                  l2.push(y2), u2 += v2.byteLength, 39 !== g2 && 40 !== g2 || this._parseSEIPayload(v2.subarray(h2), f2 + s2, "h265"), c2 += h2 + m2;
                }
                if (l2.length) {
                  var S2 = this._videoTrack, E2 = { units: l2, length: u2, isKeyframe: p2, dts: f2, cts: s2, pts: f2 + s2 };
                  p2 && (E2.fileposition = n2), S2.samples.push(E2), S2.length += u2;
                }
              }, e3.prototype._parseAV1VideoData = function(e4, t3, i3, a2, n2, o2, s2) {
                this._littleEndian, new DataView(e4, t3, i3);
                var d2, _2 = [], l2 = this._timestampBase + a2, u2 = 1 === o2;
                if (u2) {
                  var c2 = this._videoMetadata, h2 = b.parseOBUs(new Uint8Array(e4, t3, i3), c2.extra);
                  if (null == h2) return void this._onError(m.default.FORMAT_ERROR, "Flv: Invalid AV1 VideoData");
                  console.log(h2), c2.codecWidth = h2.codec_size.width, c2.codecHeight = h2.codec_size.height, c2.presentWidth = h2.present_size.width, c2.presentHeight = h2.present_size.height, c2.sarRatio = h2.sar_ratio;
                  var f2 = this._mediaInfo;
                  f2.width = c2.codecWidth, f2.height = c2.codecHeight, f2.sarNum = c2.sarRatio.width, f2.sarDen = c2.sarRatio.height, r.default.v(this.TAG, "Parsed AV1DecoderConfigurationRecord"), this._isInitialMetadataDispatched() ? this._dispatch && (this._audioTrack.length || this._videoTrack.length) && this._onDataAvailable(this._audioTrack, this._videoTrack) : this._videoInitialMetadataDispatched = true, this._dispatch = false, this._onTrackMetadata("video", c2);
                }
                if (d2 = i3, _2.push({ unitType: 0, data: new Uint8Array(e4, t3 + 0, i3) }), _2.length) {
                  var p2 = this._videoTrack, g2 = { units: _2, length: d2, isKeyframe: u2, dts: l2, cts: s2, pts: l2 + s2 };
                  u2 && (g2.fileposition = n2), p2.samples.push(g2), p2.length += d2;
                }
              }, e3;
            })(), D = k, M = (function() {
              function e3() {
              }
              return e3.prototype.destroy = function() {
                this.onError = null, this.onMediaInfo = null, this.onMetaDataArrived = null, this.onTrackMetadata = null, this.onDataAvailable = null, this.onTimedID3Metadata = null, this.onPGSSubtitleData = null, this.onSynchronousKLVMetadata = null, this.onAsynchronousKLVMetadata = null, this.onSMPTE2038Metadata = null, this.onSEI = null, this.onSCTE35Metadata = null, this.onPESPrivateData = null, this.onPESPrivateDataDescriptor = null;
              }, e3;
            })(), C = function() {
              this.program_pmt_pid = {};
            };
            !(function(e3) {
              e3[e3.kMPEG1Audio = 3] = "kMPEG1Audio", e3[e3.kMPEG2Audio = 4] = "kMPEG2Audio", e3[e3.kPESPrivateData = 6] = "kPESPrivateData", e3[e3.kADTSAAC = 15] = "kADTSAAC", e3[e3.kLOASAAC = 17] = "kLOASAAC", e3[e3.kAC3 = 129] = "kAC3", e3[e3.kEAC3 = 135] = "kEAC3", e3[e3.kMetadata = 21] = "kMetadata", e3[e3.kSCTE35 = 134] = "kSCTE35", e3[e3.kPGS = 144] = "kPGS", e3[e3.kH264 = 27] = "kH264", e3[e3.kH265 = 36] = "kH265";
            })(L || (L = {}));
            var w, I = function() {
              this.pid_stream_type = {}, this.common_pids = { h264: void 0, h265: void 0, av1: void 0, adts_aac: void 0, loas_aac: void 0, opus: void 0, ac3: void 0, eac3: void 0, mp3: void 0 }, this.pes_private_data_pids = {}, this.timed_id3_pids = {}, this.pgs_pids = {}, this.pgs_langs = {}, this.synchronous_klv_pids = {}, this.asynchronous_klv_pids = {}, this.scte_35_pids = {}, this.smpte2038_pids = {};
            }, B = function() {
            }, O = function() {
            }, P = function() {
              this.slices = [], this.total_length = 0, this.expected_length = 0, this.file_position = 0;
            };
            !(function(e3) {
              e3[e3.kUnspecified = 0] = "kUnspecified", e3[e3.kSliceNonIDR = 1] = "kSliceNonIDR", e3[e3.kSliceDPA = 2] = "kSliceDPA", e3[e3.kSliceDPB = 3] = "kSliceDPB", e3[e3.kSliceDPC = 4] = "kSliceDPC", e3[e3.kSliceIDR = 5] = "kSliceIDR", e3[e3.kSliceSEI = 6] = "kSliceSEI", e3[e3.kSliceSPS = 7] = "kSliceSPS", e3[e3.kSlicePPS = 8] = "kSlicePPS", e3[e3.kSliceAUD = 9] = "kSliceAUD", e3[e3.kEndOfSequence = 10] = "kEndOfSequence", e3[e3.kEndOfStream = 11] = "kEndOfStream", e3[e3.kFiller = 12] = "kFiller", e3[e3.kSPSExt = 13] = "kSPSExt", e3[e3.kReserved0 = 14] = "kReserved0";
            })(w || (w = {}));
            var x, U, N = function() {
            }, V = function(e3) {
              var t3 = e3.data.byteLength;
              this.type = e3.type, this.data = new Uint8Array(4 + t3), new DataView(this.data.buffer).setUint32(0, t3), this.data.set(e3.data, 4);
            }, G = (function() {
              function e3(e4) {
                this.TAG = "H264AnnexBParser", this.current_startcode_offset_ = 0, this.eof_flag_ = false, this.data_ = e4, this.current_startcode_offset_ = this.findNextStartCodeOffset(0), this.eof_flag_ && r.default.e(this.TAG, "Could not find H264 startcode until payload end!");
              }
              return e3.prototype.findNextStartCodeOffset = function(e4) {
                for (var t3 = e4, i3 = this.data_; ; ) {
                  if (t3 + 3 >= i3.byteLength) return this.eof_flag_ = true, i3.byteLength;
                  var a2 = i3[t3 + 0] << 24 | i3[t3 + 1] << 16 | i3[t3 + 2] << 8 | i3[t3 + 3], n2 = i3[t3 + 0] << 16 | i3[t3 + 1] << 8 | i3[t3 + 2];
                  if (1 === a2 || 1 === n2) return t3;
                  t3++;
                }
              }, e3.prototype.readNextNaluPayload = function() {
                for (var e4 = this.data_, t3 = null; null == t3 && !this.eof_flag_; ) {
                  var i3 = this.current_startcode_offset_, a2 = 31 & e4[i3 += 1 == (e4[i3] << 24 | e4[i3 + 1] << 16 | e4[i3 + 2] << 8 | e4[i3 + 3]) ? 4 : 3], n2 = (128 & e4[i3]) >>> 7, r2 = this.findNextStartCodeOffset(i3);
                  if (this.current_startcode_offset_ = r2, !(a2 >= w.kReserved0) && 0 === n2) {
                    var o2 = e4.subarray(i3, r2);
                    (t3 = new N()).type = a2, t3.data = o2;
                  }
                }
                return t3;
              }, e3;
            })(), F = (function() {
              function e3(e4, t3, i3) {
                var a2 = 8 + e4.byteLength + 1 + 2 + t3.byteLength, n2 = false;
                66 !== e4[3] && 77 !== e4[3] && 88 !== e4[3] && (n2 = true, a2 += 4);
                var r2 = this.data = new Uint8Array(a2);
                r2[0] = 1, r2[1] = e4[1], r2[2] = e4[2], r2[3] = e4[3], r2[4] = 255, r2[5] = 225;
                var o2 = e4.byteLength;
                r2[6] = o2 >>> 8, r2[7] = 255 & o2;
                var s2 = 8;
                r2.set(e4, 8), r2[s2 += o2] = 1;
                var d2 = t3.byteLength;
                r2[s2 + 1] = d2 >>> 8, r2[s2 + 2] = 255 & d2, r2.set(t3, s2 + 3), s2 += 3 + d2, n2 && (r2[s2] = 252 | i3.chroma_format_idc, r2[s2 + 1] = 248 | i3.bit_depth_luma - 8, r2[s2 + 2] = 248 | i3.bit_depth_chroma - 8, r2[s2 + 3] = 0, s2 += 4);
              }
              return e3.prototype.getData = function() {
                return this.data;
              }, e3;
            })();
            !(function(e3) {
              e3[e3.kNull = 0] = "kNull", e3[e3.kAACMain = 1] = "kAACMain", e3[e3.kAAC_LC = 2] = "kAAC_LC", e3[e3.kAAC_SSR = 3] = "kAAC_SSR", e3[e3.kAAC_LTP = 4] = "kAAC_LTP", e3[e3.kAAC_SBR = 5] = "kAAC_SBR", e3[e3.kAAC_Scalable = 6] = "kAAC_Scalable", e3[e3.kLayer1 = 32] = "kLayer1", e3[e3.kLayer2 = 33] = "kLayer2", e3[e3.kLayer3 = 34] = "kLayer3";
            })(x || (x = {})), (function(e3) {
              e3[e3.k96000Hz = 0] = "k96000Hz", e3[e3.k88200Hz = 1] = "k88200Hz", e3[e3.k64000Hz = 2] = "k64000Hz", e3[e3.k48000Hz = 3] = "k48000Hz", e3[e3.k44100Hz = 4] = "k44100Hz", e3[e3.k32000Hz = 5] = "k32000Hz", e3[e3.k24000Hz = 6] = "k24000Hz", e3[e3.k22050Hz = 7] = "k22050Hz", e3[e3.k16000Hz = 8] = "k16000Hz", e3[e3.k12000Hz = 9] = "k12000Hz", e3[e3.k11025Hz = 10] = "k11025Hz", e3[e3.k8000Hz = 11] = "k8000Hz", e3[e3.k7350Hz = 12] = "k7350Hz";
            })(U || (U = {}));
            var j, z, H = [96e3, 88200, 64e3, 48e3, 44100, 32e3, 24e3, 22050, 16e3, 12e3, 11025, 8e3, 7350], q = (j = function(e3, t3) {
              return j = Object.setPrototypeOf || { __proto__: [] } instanceof Array && function(e4, t4) {
                e4.__proto__ = t4;
              } || function(e4, t4) {
                for (var i3 in t4) Object.prototype.hasOwnProperty.call(t4, i3) && (e4[i3] = t4[i3]);
              }, j(e3, t3);
            }, function(e3, t3) {
              if ("function" != typeof t3 && null !== t3) throw new TypeError("Class extends value " + String(t3) + " is not a constructor or null");
              function i3() {
                this.constructor = e3;
              }
              j(e3, t3), e3.prototype = null === t3 ? Object.create(t3) : (i3.prototype = t3.prototype, new i3());
            }), K = function() {
            }, W = (function(e3) {
              function t3() {
                return null !== e3 && e3.apply(this, arguments) || this;
              }
              return q(t3, e3), t3;
            })(K), Y = (function() {
              function e3(e4) {
                this.TAG = "AACADTSParser", this.data_ = e4, this.current_syncword_offset_ = this.findNextSyncwordOffset(0), this.eof_flag_ && r.default.e(this.TAG, "Could not found ADTS syncword until payload end");
              }
              return e3.prototype.findNextSyncwordOffset = function(e4) {
                for (var t3 = e4, i3 = this.data_; ; ) {
                  if (t3 + 7 >= i3.byteLength) return this.eof_flag_ = true, i3.byteLength;
                  if (4095 == (i3[t3 + 0] << 8 | i3[t3 + 1]) >>> 4) return t3;
                  t3++;
                }
              }, e3.prototype.readNextAACFrame = function() {
                for (var e4 = this.data_, t3 = null; null == t3 && !this.eof_flag_; ) {
                  var i3 = this.current_syncword_offset_, a2 = (8 & e4[i3 + 1]) >>> 3, n2 = (6 & e4[i3 + 1]) >>> 1, r2 = 1 & e4[i3 + 1], o2 = (192 & e4[i3 + 2]) >>> 6, s2 = (60 & e4[i3 + 2]) >>> 2, d2 = (1 & e4[i3 + 2]) << 2 | (192 & e4[i3 + 3]) >>> 6, _2 = (3 & e4[i3 + 3]) << 11 | e4[i3 + 4] << 3 | (224 & e4[i3 + 5]) >>> 5;
                  if (e4[i3 + 6], i3 + _2 > this.data_.byteLength) {
                    this.eof_flag_ = true, this.has_last_incomplete_data = true;
                    break;
                  }
                  var l2 = 1 === r2 ? 7 : 9, u2 = _2 - l2;
                  i3 += l2;
                  var c2 = this.findNextSyncwordOffset(i3 + u2);
                  if (this.current_syncword_offset_ = c2, (0 === a2 || 1 === a2) && 0 === n2) {
                    var h2 = e4.subarray(i3, i3 + u2);
                    (t3 = new K()).audio_object_type = o2 + 1, t3.sampling_freq_index = s2, t3.sampling_frequency = H[s2], t3.channel_config = d2, t3.data = h2;
                  }
                }
                return t3;
              }, e3.prototype.hasIncompleteData = function() {
                return this.has_last_incomplete_data;
              }, e3.prototype.getIncompleteData = function() {
                return this.has_last_incomplete_data ? this.data_.subarray(this.current_syncword_offset_) : null;
              }, e3;
            })(), X = (function() {
              function e3(e4) {
                this.TAG = "AACLOASParser", this.data_ = e4, this.current_syncword_offset_ = this.findNextSyncwordOffset(0), this.eof_flag_ && r.default.e(this.TAG, "Could not found LOAS syncword until payload end");
              }
              return e3.prototype.findNextSyncwordOffset = function(e4) {
                for (var t3 = e4, i3 = this.data_; ; ) {
                  if (t3 + 1 >= i3.byteLength) return this.eof_flag_ = true, i3.byteLength;
                  if (695 == (i3[t3 + 0] << 3 | i3[t3 + 1] >>> 5)) return t3;
                  t3++;
                }
              }, e3.prototype.getLATMValue = function(e4) {
                for (var t3 = e4.readBits(2), i3 = 0, a2 = 0; a2 <= t3; a2++) i3 <<= 8, i3 |= e4.readByte();
                return i3;
              }, e3.prototype.readNextAACFrame = function(e4) {
                for (var t3 = this.data_, i3 = null; null == i3 && !this.eof_flag_; ) {
                  var a2 = this.current_syncword_offset_, n2 = (31 & t3[a2 + 1]) << 8 | t3[a2 + 2];
                  if (a2 + 3 + n2 >= this.data_.byteLength) {
                    this.eof_flag_ = true, this.has_last_incomplete_data = true;
                    break;
                  }
                  var o2 = new f(t3.subarray(a2 + 3, a2 + 3 + n2)), s2 = null;
                  if (o2.readBool()) {
                    if (null == e4) {
                      r.default.w(this.TAG, "StreamMuxConfig Missing"), this.current_syncword_offset_ = this.findNextSyncwordOffset(a2 + 3 + n2), o2.destroy();
                      continue;
                    }
                    s2 = e4;
                  } else {
                    var d2 = o2.readBool();
                    if (d2 && o2.readBool()) {
                      r.default.e(this.TAG, "audioMuxVersionA is Not Supported"), o2.destroy();
                      break;
                    }
                    if (d2 && this.getLATMValue(o2), !o2.readBool()) {
                      r.default.e(this.TAG, "allStreamsSameTimeFraming zero is Not Supported"), o2.destroy();
                      break;
                    }
                    if (0 !== o2.readBits(6)) {
                      r.default.e(this.TAG, "more than 2 numSubFrames Not Supported"), o2.destroy();
                      break;
                    }
                    if (0 !== o2.readBits(4)) {
                      r.default.e(this.TAG, "more than 2 numProgram Not Supported"), o2.destroy();
                      break;
                    }
                    if (0 !== o2.readBits(3)) {
                      r.default.e(this.TAG, "more than 2 numLayer Not Supported"), o2.destroy();
                      break;
                    }
                    var _2 = d2 ? this.getLATMValue(o2) : 0, l2 = o2.readBits(5);
                    _2 -= 5;
                    var u2 = o2.readBits(4);
                    _2 -= 4;
                    var c2 = o2.readBits(4);
                    _2 -= 4, o2.readBits(3), (_2 -= 3) > 0 && o2.readBits(_2);
                    var h2 = o2.readBits(3);
                    if (0 !== h2) {
                      r.default.e(this.TAG, "frameLengthType = ".concat(h2, ". Only frameLengthType = 0 Supported")), o2.destroy();
                      break;
                    }
                    o2.readByte();
                    var p2 = o2.readBool();
                    if (p2) if (d2) this.getLATMValue(o2);
                    else {
                      for (var m2 = 0; ; ) {
                        m2 <<= 8;
                        var g2 = o2.readBool();
                        if (m2 += o2.readByte(), !g2) break;
                      }
                      console.log(m2);
                    }
                    o2.readBool() && o2.readByte(), (s2 = new W()).audio_object_type = l2, s2.sampling_freq_index = u2, s2.sampling_frequency = H[s2.sampling_freq_index], s2.channel_config = c2, s2.other_data_present = p2;
                  }
                  for (var v2 = 0; ; ) {
                    var y2 = o2.readByte();
                    if (v2 += y2, 255 !== y2) break;
                  }
                  for (var S2 = new Uint8Array(v2), E2 = 0; E2 < v2; E2++) S2[E2] = o2.readByte();
                  (i3 = new W()).audio_object_type = s2.audio_object_type, i3.sampling_freq_index = s2.sampling_freq_index, i3.sampling_frequency = H[s2.sampling_freq_index], i3.channel_config = s2.channel_config, i3.other_data_present = s2.other_data_present, i3.data = S2, this.current_syncword_offset_ = this.findNextSyncwordOffset(a2 + 3 + n2);
                }
                return i3;
              }, e3.prototype.hasIncompleteData = function() {
                return this.has_last_incomplete_data;
              }, e3.prototype.getIncompleteData = function() {
                return this.has_last_incomplete_data ? this.data_.subarray(this.current_syncword_offset_) : null;
              }, e3;
            })(), Q = function(e3) {
              var t3 = null, i3 = e3.audio_object_type, a2 = e3.audio_object_type, n2 = e3.sampling_freq_index, r2 = e3.channel_config, o2 = 0, s2 = navigator.userAgent.toLowerCase();
              -1 !== s2.indexOf("firefox") ? n2 >= 6 ? (a2 = 5, t3 = new Array(4), o2 = n2 - 3) : (a2 = 2, t3 = new Array(2), o2 = n2) : -1 !== s2.indexOf("android") ? (a2 = 2, t3 = new Array(2), o2 = n2) : (a2 = 5, o2 = n2, t3 = new Array(4), n2 >= 6 ? o2 = n2 - 3 : 1 === r2 && (a2 = 2, t3 = new Array(2), o2 = n2)), t3[0] = a2 << 3, t3[0] |= (15 & n2) >>> 1, t3[1] = (15 & n2) << 7, t3[1] |= (15 & r2) << 3, 5 === a2 && (t3[1] |= (15 & o2) >>> 1, t3[2] = (1 & o2) << 7, t3[2] |= 8, t3[3] = 0), this.config = t3, this.sampling_rate = H[n2], this.channel_count = r2, this.codec_mimetype = "mp4a.40." + a2, this.original_codec_mimetype = "mp4a.40." + i3;
            }, J = function() {
            }, Z = function() {
            };
            !(function(e3) {
              e3[e3.kSpliceNull = 0] = "kSpliceNull", e3[e3.kSpliceSchedule = 4] = "kSpliceSchedule", e3[e3.kSpliceInsert = 5] = "kSpliceInsert", e3[e3.kTimeSignal = 6] = "kTimeSignal", e3[e3.kBandwidthReservation = 7] = "kBandwidthReservation", e3[e3.kPrivateCommand = 255] = "kPrivateCommand";
            })(z || (z = {}));
            var $, ee = function(e3) {
              var t3 = e3.readBool();
              return t3 ? (e3.readBits(6), { time_specified_flag: t3, pts_time: 4 * e3.readBits(31) + e3.readBits(2) }) : (e3.readBits(7), { time_specified_flag: t3 });
            }, te = function(e3) {
              var t3 = e3.readBool();
              return e3.readBits(6), { auto_return: t3, duration: 4 * e3.readBits(31) + e3.readBits(2) };
            }, ie = function(e3, t3) {
              var i3 = t3.readBits(8);
              return e3 ? { component_tag: i3 } : { component_tag: i3, splice_time: ee(t3) };
            }, ae = function(e3) {
              return { component_tag: e3.readBits(8), utc_splice_time: e3.readBits(32) };
            }, ne = function(e3) {
              var t3 = e3.readBits(32), i3 = e3.readBool();
              e3.readBits(7);
              var a2 = { splice_event_id: t3, splice_event_cancel_indicator: i3 };
              if (i3) return a2;
              if (a2.out_of_network_indicator = e3.readBool(), a2.program_splice_flag = e3.readBool(), a2.duration_flag = e3.readBool(), e3.readBits(5), a2.program_splice_flag) a2.utc_splice_time = e3.readBits(32);
              else {
                a2.component_count = e3.readBits(8), a2.components = [];
                for (var n2 = 0; n2 < a2.component_count; n2++) a2.components.push(ae(e3));
              }
              return a2.duration_flag && (a2.break_duration = te(e3)), a2.unique_program_id = e3.readBits(16), a2.avail_num = e3.readBits(8), a2.avails_expected = e3.readBits(8), a2;
            }, re = function(e3, t3, i3, a2) {
              return { descriptor_tag: e3, descriptor_length: t3, identifier: i3, provider_avail_id: a2.readBits(32) };
            }, oe = function(e3, t3, i3, a2) {
              var n2 = a2.readBits(8), r2 = a2.readBits(3);
              a2.readBits(5);
              for (var o2 = "", s2 = 0; s2 < r2; s2++) o2 += String.fromCharCode(a2.readBits(8));
              return { descriptor_tag: e3, descriptor_length: t3, identifier: i3, preroll: n2, dtmf_count: r2, DTMF_char: o2 };
            }, se = function(e3) {
              var t3 = e3.readBits(8);
              return e3.readBits(7), { component_tag: t3, pts_offset: 4 * e3.readBits(31) + e3.readBits(2) };
            }, de = function(e3, t3, i3, a2) {
              var n2 = a2.readBits(32), r2 = a2.readBool();
              a2.readBits(7);
              var o2 = { descriptor_tag: e3, descriptor_length: t3, identifier: i3, segmentation_event_id: n2, segmentation_event_cancel_indicator: r2 };
              if (r2) return o2;
              if (o2.program_segmentation_flag = a2.readBool(), o2.segmentation_duration_flag = a2.readBool(), o2.delivery_not_restricted_flag = a2.readBool(), o2.delivery_not_restricted_flag ? a2.readBits(5) : (o2.web_delivery_allowed_flag = a2.readBool(), o2.no_regional_blackout_flag = a2.readBool(), o2.archive_allowed_flag = a2.readBool(), o2.device_restrictions = a2.readBits(2)), !o2.program_segmentation_flag) {
                o2.component_count = a2.readBits(8), o2.components = [];
                for (var s2 = 0; s2 < o2.component_count; s2++) o2.components.push(se(a2));
              }
              o2.segmentation_duration_flag && (o2.segmentation_duration = a2.readBits(40)), o2.segmentation_upid_type = a2.readBits(8), o2.segmentation_upid_length = a2.readBits(8);
              var d2 = new Uint8Array(o2.segmentation_upid_length);
              for (s2 = 0; s2 < o2.segmentation_upid_length; s2++) d2[s2] = a2.readBits(8);
              return o2.segmentation_upid = d2.buffer, o2.segmentation_type_id = a2.readBits(8), o2.segment_num = a2.readBits(8), o2.segments_expected = a2.readBits(8), 52 !== o2.segmentation_type_id && 54 !== o2.segmentation_type_id && 56 !== o2.segmentation_type_id && 58 !== o2.segmentation_type_id || (o2.sub_segment_num = a2.readBits(8), o2.sub_segments_expected = a2.readBits(8)), o2;
            }, _e = function(e3, t3, i3, a2) {
              return { descriptor_tag: e3, descriptor_length: t3, identifier: i3, TAI_seconds: a2.readBits(48), TAI_ns: a2.readBits(32), UTC_offset: a2.readBits(16) };
            }, le = function(e3) {
              return { component_tag: e3.readBits(8), ISO_code: String.fromCharCode(e3.readBits(8), e3.readBits(8), e3.readBits(8)), Bit_Stream_Mode: e3.readBits(3), Num_Channels: e3.readBits(4), Full_Srvc_Audio: e3.readBool() };
            }, ue = function(e3, t3, i3, a2) {
              for (var n2 = a2.readBits(4), r2 = [], o2 = 0; o2 < n2; o2++) r2.push(le(a2));
              return { descriptor_tag: e3, descriptor_length: t3, identifier: i3, audio_count: n2, components: r2 };
            };
            !(function(e3) {
              e3[e3.kSliceIDR_W_RADL = 19] = "kSliceIDR_W_RADL", e3[e3.kSliceIDR_N_LP = 20] = "kSliceIDR_N_LP", e3[e3.kSliceCRA_NUT = 21] = "kSliceCRA_NUT", e3[e3.kSliceVPS = 32] = "kSliceVPS", e3[e3.kSliceSPS = 33] = "kSliceSPS", e3[e3.kSlicePPS = 34] = "kSlicePPS", e3[e3.kSliceAUD = 35] = "kSliceAUD", e3[e3.kSliceSEI = 39] = "kSliceSEI", e3[e3.kSliceSEISuffix = 40] = "kSliceSEISuffix";
            })($ || ($ = {}));
            var ce = function() {
            }, he = function(e3) {
              var t3 = e3.data.byteLength;
              this.type = e3.type, this.data = new Uint8Array(4 + t3), new DataView(this.data.buffer).setUint32(0, t3), this.data.set(e3.data, 4);
            }, fe = (function() {
              function e3(e4) {
                this.TAG = "H265AnnexBParser", this.current_startcode_offset_ = 0, this.eof_flag_ = false, this.data_ = e4, this.current_startcode_offset_ = this.findNextStartCodeOffset(0), this.eof_flag_ && r.default.e(this.TAG, "Could not find H265 startcode until payload end!");
              }
              return e3.prototype.findNextStartCodeOffset = function(e4) {
                for (var t3 = e4, i3 = this.data_; ; ) {
                  if (t3 + 3 >= i3.byteLength) return this.eof_flag_ = true, i3.byteLength;
                  var a2 = i3[t3 + 0] << 24 | i3[t3 + 1] << 16 | i3[t3 + 2] << 8 | i3[t3 + 3], n2 = i3[t3 + 0] << 16 | i3[t3 + 1] << 8 | i3[t3 + 2];
                  if (1 === a2 || 1 === n2) return t3;
                  t3++;
                }
              }, e3.prototype.readNextNaluPayload = function() {
                for (var e4 = this.data_, t3 = null; null == t3 && !this.eof_flag_; ) {
                  var i3 = this.current_startcode_offset_, a2 = e4[i3 += 1 == (e4[i3] << 24 | e4[i3 + 1] << 16 | e4[i3 + 2] << 8 | e4[i3 + 3]) ? 4 : 3] >> 1 & 63, n2 = (128 & e4[i3]) >>> 7, r2 = this.findNextStartCodeOffset(i3);
                  if (this.current_startcode_offset_ = r2, 0 === n2) {
                    var o2 = e4.subarray(i3, r2);
                    (t3 = new ce()).type = a2, t3.data = o2;
                  }
                }
                return t3;
              }, e3;
            })(), pe = (function() {
              function e3(e4, t3, i3, a2) {
                var n2 = 23 + (5 + e4.byteLength) + (5 + t3.byteLength) + (5 + i3.byteLength), r2 = this.data = new Uint8Array(n2);
                r2[0] = 1, r2[1] = (3 & a2.general_profile_space) << 6 | (a2.general_tier_flag ? 1 : 0) << 5 | 31 & a2.general_profile_idc, r2[2] = a2.general_profile_compatibility_flags_1, r2[3] = a2.general_profile_compatibility_flags_2, r2[4] = a2.general_profile_compatibility_flags_3, r2[5] = a2.general_profile_compatibility_flags_4, r2[6] = a2.general_constraint_indicator_flags_1, r2[7] = a2.general_constraint_indicator_flags_2, r2[8] = a2.general_constraint_indicator_flags_3, r2[9] = a2.general_constraint_indicator_flags_4, r2[10] = a2.general_constraint_indicator_flags_5, r2[11] = a2.general_constraint_indicator_flags_6, r2[12] = a2.general_level_idc, r2[13] = 240 | (3840 & a2.min_spatial_segmentation_idc) >> 8, r2[14] = 255 & a2.min_spatial_segmentation_idc, r2[15] = 252 | 3 & a2.parallelismType, r2[16] = 252 | 3 & a2.chroma_format_idc, r2[17] = 248 | 7 & a2.bit_depth_luma_minus8, r2[18] = 248 | 7 & a2.bit_depth_chroma_minus8, r2[19] = 0, r2[20] = 0, r2[21] = (3 & a2.constant_frame_rate) << 6 | (7 & a2.num_temporal_layers) << 3 | (a2.temporal_id_nested ? 1 : 0) << 2 | 3, r2[22] = 3, r2[23] = 128 | $.kSliceVPS, r2[24] = 0, r2[25] = 1, r2[26] = (65280 & e4.byteLength) >> 8, r2[27] = 255 & e4.byteLength, r2.set(e4, 28), r2[23 + (5 + e4.byteLength) + 0] = 128 | $.kSliceSPS, r2[23 + (5 + e4.byteLength) + 1] = 0, r2[23 + (5 + e4.byteLength) + 2] = 1, r2[23 + (5 + e4.byteLength) + 3] = (65280 & t3.byteLength) >> 8, r2[23 + (5 + e4.byteLength) + 4] = 255 & t3.byteLength, r2.set(t3, 23 + (5 + e4.byteLength) + 5), r2[23 + (5 + e4.byteLength + 5 + t3.byteLength) + 0] = 128 | $.kSlicePPS, r2[23 + (5 + e4.byteLength + 5 + t3.byteLength) + 1] = 0, r2[23 + (5 + e4.byteLength + 5 + t3.byteLength) + 2] = 1, r2[23 + (5 + e4.byteLength + 5 + t3.byteLength) + 3] = (65280 & i3.byteLength) >> 8, r2[23 + (5 + e4.byteLength + 5 + t3.byteLength) + 4] = 255 & i3.byteLength, r2.set(i3, 23 + (5 + e4.byteLength + 5 + t3.byteLength) + 5);
              }
              return e3.prototype.getData = function() {
                return this.data;
              }, e3;
            })(), me = function() {
            }, ge = function() {
            }, ve = function() {
            }, ye = [[64, 64, 80, 80, 96, 96, 112, 112, 128, 128, 160, 160, 192, 192, 224, 224, 256, 256, 320, 320, 384, 384, 448, 448, 512, 512, 640, 640, 768, 768, 896, 896, 1024, 1024, 1152, 1152, 1280, 1280], [69, 70, 87, 88, 104, 105, 121, 122, 139, 140, 174, 175, 208, 209, 243, 244, 278, 279, 348, 349, 417, 418, 487, 488, 557, 558, 696, 697, 835, 836, 975, 976, 1114, 1115, 1253, 1254, 1393, 1394], [96, 96, 120, 120, 144, 144, 168, 168, 192, 192, 240, 240, 288, 288, 336, 336, 384, 384, 480, 480, 576, 576, 672, 672, 768, 768, 960, 960, 1152, 1152, 1344, 1344, 1536, 1536, 1728, 1728, 1920, 1920]], Se = (function() {
              function e3(e4) {
                this.TAG = "AC3Parser", this.data_ = e4, this.current_syncword_offset_ = this.findNextSyncwordOffset(0), this.eof_flag_ && r.default.e(this.TAG, "Could not found AC3 syncword until payload end");
              }
              return e3.prototype.findNextSyncwordOffset = function(e4) {
                for (var t3 = e4, i3 = this.data_; ; ) {
                  if (t3 + 7 >= i3.byteLength) return this.eof_flag_ = true, i3.byteLength;
                  if (2935 == (i3[t3 + 0] << 8 | i3[t3 + 1])) return t3;
                  t3++;
                }
              }, e3.prototype.readNextAC3Frame = function() {
                for (var e4 = this.data_, t3 = null; null == t3 && !this.eof_flag_; ) {
                  var i3 = this.current_syncword_offset_, a2 = e4[i3 + 4] >> 6, n2 = [48e3, 44200, 33e3][a2], r2 = 63 & e4[i3 + 4], o2 = 2 * ye[a2][r2];
                  if (isNaN(o2) || i3 + o2 > this.data_.byteLength) {
                    this.eof_flag_ = true, this.has_last_incomplete_data = true;
                    break;
                  }
                  var s2 = this.findNextSyncwordOffset(i3 + o2);
                  this.current_syncword_offset_ = s2;
                  var d2 = e4[i3 + 5] >> 3, _2 = 7 & e4[i3 + 5], l2 = e4[i3 + 6] >> 5, u2 = 0;
                  1 & l2 && 1 !== l2 && (u2 += 2), 4 & l2 && (u2 += 2), 2 === l2 && (u2 += 2);
                  var c2 = (e4[i3 + 6] << 8 | e4[i3 + 7]) >> 12 - u2 & 1, h2 = [2, 1, 2, 3, 3, 4, 4, 5][l2] + c2;
                  (t3 = new ve()).sampling_frequency = n2, t3.channel_count = h2, t3.channel_mode = l2, t3.bit_stream_identification = d2, t3.low_frequency_effects_channel_on = c2, t3.bit_stream_mode = _2, t3.frame_size_code = r2, t3.data = e4.subarray(i3, i3 + o2);
                }
                return t3;
              }, e3.prototype.hasIncompleteData = function() {
                return this.has_last_incomplete_data;
              }, e3.prototype.getIncompleteData = function() {
                return this.has_last_incomplete_data ? this.data_.subarray(this.current_syncword_offset_) : null;
              }, e3;
            })(), Ee = function(e3) {
              var t3;
              t3 = [e3.sampling_rate_code << 6 | e3.bit_stream_identification << 1 | e3.bit_stream_mode >> 2, (3 & e3.bit_stream_mode) << 6 | e3.channel_mode << 3 | e3.low_frequency_effects_channel_on << 2 | e3.frame_size_code >> 4, e3.frame_size_code << 4 & 224], this.config = t3, this.sampling_rate = e3.sampling_frequency, this.bit_stream_identification = e3.bit_stream_identification, this.bit_stream_mode = e3.bit_stream_mode, this.low_frequency_effects_channel_on = e3.low_frequency_effects_channel_on, this.channel_count = e3.channel_count, this.channel_mode = e3.channel_mode, this.codec_mimetype = "ac-3", this.original_codec_mimetype = "ac-3";
            }, Ae = function() {
            }, be = (function() {
              function e3(e4) {
                this.TAG = "EAC3Parser", this.data_ = e4, this.current_syncword_offset_ = this.findNextSyncwordOffset(0), this.eof_flag_ && r.default.e(this.TAG, "Could not found AC3 syncword until payload end");
              }
              return e3.prototype.findNextSyncwordOffset = function(e4) {
                for (var t3 = e4, i3 = this.data_; ; ) {
                  if (t3 + 7 >= i3.byteLength) return this.eof_flag_ = true, i3.byteLength;
                  if (2935 == (i3[t3 + 0] << 8 | i3[t3 + 1])) return t3;
                  t3++;
                }
              }, e3.prototype.readNextEAC3Frame = function() {
                for (var e4 = this.data_, t3 = null; null == t3 && !this.eof_flag_; ) {
                  var i3 = this.current_syncword_offset_, a2 = new f(e4.subarray(i3 + 2)), n2 = (a2.readBits(2), a2.readBits(3), a2.readBits(11) + 1 << 1), r2 = a2.readBits(2), o2 = null, s2 = null;
                  3 === r2 ? (o2 = [24e3, 22060, 16e3][r2 = a2.readBits(2)], s2 = 3) : (o2 = [48e3, 44100, 32e3][r2], s2 = a2.readBits(2));
                  var d2 = a2.readBits(3), _2 = a2.readBits(1), l2 = a2.readBits(5);
                  if (i3 + n2 > this.data_.byteLength) {
                    this.eof_flag_ = true, this.has_last_incomplete_data = true;
                    break;
                  }
                  var u2 = this.findNextSyncwordOffset(i3 + n2);
                  this.current_syncword_offset_ = u2;
                  var c2 = [2, 1, 2, 3, 3, 4, 4, 5][d2] + _2;
                  a2.destroy(), (t3 = new Ae()).sampling_frequency = o2, t3.channel_count = c2, t3.channel_mode = d2, t3.bit_stream_identification = l2, t3.low_frequency_effects_channel_on = _2, t3.frame_size = n2, t3.num_blks = [1, 2, 3, 6][s2], t3.data = e4.subarray(i3, i3 + n2);
                }
                return t3;
              }, e3.prototype.hasIncompleteData = function() {
                return this.has_last_incomplete_data;
              }, e3.prototype.getIncompleteData = function() {
                return this.has_last_incomplete_data ? this.data_.subarray(this.current_syncword_offset_) : null;
              }, e3;
            })(), Re = function(e3) {
              var t3, i3 = Math.floor(e3.frame_size * e3.sampling_frequency / (16 * e3.num_blks));
              t3 = [255 & i3, 248 & i3, e3.sampling_rate_code << 6 | e3.bit_stream_identification << 1, e3.channel_mode << 1 | e3.low_frequency_effects_channel_on, 0], this.config = t3, this.sampling_rate = e3.sampling_frequency, this.bit_stream_identification = e3.bit_stream_identification, this.num_blks = e3.num_blks, this.low_frequency_effects_channel_on = e3.low_frequency_effects_channel_on, this.channel_count = e3.channel_count, this.channel_mode = e3.channel_mode, this.codec_mimetype = "ec-3", this.original_codec_mimetype = "ec-3";
            }, Te = function() {
            }, Le = (function() {
              function e3(e4) {
                this.TAG = "AV1OBUInMpegTsParser", this.current_startcode_offset_ = 0, this.eof_flag_ = false, this.data_ = e4, this.current_startcode_offset_ = this.findNextStartCodeOffset(0), this.eof_flag_ && r.default.e(this.TAG, "Could not find AV1 startcode until payload end!");
              }
              return e3._ebsp2rbsp = function(e4) {
                for (var t3 = e4, i3 = t3.byteLength, a2 = new Uint8Array(i3), n2 = 0, r2 = 0; r2 < i3; r2++) r2 >= 2 && 3 === t3[r2] && 0 === t3[r2 - 1] && 0 === t3[r2 - 2] || (a2[n2] = t3[r2], n2++);
                return new Uint8Array(a2.buffer, 0, n2);
              }, e3.prototype.findNextStartCodeOffset = function(e4) {
                for (var t3 = e4, i3 = this.data_; ; ) {
                  if (t3 + 2 >= i3.byteLength) return this.eof_flag_ = true, i3.byteLength;
                  if (1 == (i3[t3 + 0] << 16 | i3[t3 + 1] << 8 | i3[t3 + 2])) return t3;
                  t3++;
                }
              }, e3.prototype.readNextOBUPayload = function() {
                for (var t3 = this.data_, i3 = null; null == i3 && !this.eof_flag_; ) {
                  var a2 = this.current_startcode_offset_ + 3, n2 = this.findNextStartCodeOffset(a2);
                  this.current_startcode_offset_ = n2, i3 = e3._ebsp2rbsp(t3.subarray(a2, n2));
                }
                return i3;
              }, e3;
            })(), ke = function() {
            }, De = /* @__PURE__ */ (function() {
              var e3 = function(t3, i3) {
                return e3 = Object.setPrototypeOf || { __proto__: [] } instanceof Array && function(e4, t4) {
                  e4.__proto__ = t4;
                } || function(e4, t4) {
                  for (var i4 in t4) Object.prototype.hasOwnProperty.call(t4, i4) && (e4[i4] = t4[i4]);
                }, e3(t3, i3);
              };
              return function(t3, i3) {
                if ("function" != typeof i3 && null !== i3) throw new TypeError("Class extends value " + String(i3) + " is not a constructor or null");
                function a2() {
                  this.constructor = t3;
                }
                e3(t3, i3), t3.prototype = null === i3 ? Object.create(i3) : (a2.prototype = i3.prototype, new a2());
              };
            })(), Me = function() {
              return Me = Object.assign || function(e3) {
                for (var t3, i3 = 1, a2 = arguments.length; i3 < a2; i3++) for (var n2 in t3 = arguments[i3]) Object.prototype.hasOwnProperty.call(t3, n2) && (e3[n2] = t3[n2]);
                return e3;
              }, Me.apply(this, arguments);
            }, Ce = (function(e3) {
              function t3(t4, i3) {
                var a2 = e3.call(this) || this;
                return a2.TAG = "TSDemuxer", a2.first_parse_ = true, a2.media_info_ = new s.default(), a2.timescale_ = 90, a2.duration_ = 0, a2.current_pmt_pid_ = -1, a2.program_pmt_map_ = {}, a2.pes_slice_queues_ = {}, a2.section_slice_queues_ = {}, a2.video_metadata_ = { vps: void 0, sps: void 0, pps: void 0, av1c: void 0, details: void 0 }, a2.audio_metadata_ = { codec: void 0, audio_object_type: void 0, sampling_freq_index: void 0, sampling_frequency: void 0, channel_config: void 0 }, a2.last_pcr_base_ = NaN, a2.timestamp_offset_ = 0, a2.audio_last_sample_pts_ = void 0, a2.aac_last_incomplete_data_ = null, a2.has_video_ = false, a2.has_audio_ = false, a2.video_init_segment_dispatched_ = false, a2.audio_init_segment_dispatched_ = false, a2.video_metadata_changed_ = false, a2.audio_metadata_changed_ = false, a2.loas_previous_frame = null, a2.video_track_ = { type: "video", id: 1, sequenceNumber: 0, samples: [], length: 0 }, a2.audio_track_ = { type: "audio", id: 2, sequenceNumber: 0, samples: [], length: 0 }, a2.ts_packet_size_ = t4.ts_packet_size, a2.sync_offset_ = t4.sync_offset, a2.config_ = i3, a2;
              }
              return De(t3, e3), t3.prototype.destroy = function() {
                this.media_info_ = null, this.pes_slice_queues_ = null, this.section_slice_queues_ = null, this.video_metadata_ = null, this.audio_metadata_ = null, this.aac_last_incomplete_data_ = null, this.video_track_ = null, this.audio_track_ = null, e3.prototype.destroy.call(this);
              }, t3.probe = function(e4) {
                var t4 = new Uint8Array(e4), i3 = -1, a2 = 188;
                if (t4.byteLength <= 3 * a2) return { needMoreData: true };
                for (; -1 === i3; ) {
                  for (var n2 = Math.min(1e3, t4.byteLength - 3 * a2), o2 = 0; o2 < n2; ) {
                    if (71 === t4[o2] && 71 === t4[o2 + a2] && 71 === t4[o2 + 2 * a2]) {
                      i3 = o2;
                      break;
                    }
                    o2++;
                  }
                  if (-1 === i3) if (188 === a2) a2 = 192;
                  else {
                    if (192 !== a2) break;
                    a2 = 204;
                  }
                }
                return -1 === i3 ? { match: false } : (192 === a2 && i3 >= 4 ? (r.default.v("TSDemuxer", "ts_packet_size = 192, m2ts mode"), i3 -= 4) : 204 === a2 && r.default.v("TSDemuxer", "ts_packet_size = 204, RS encoded MPEG2-TS stream"), { match: true, consumed: 0, ts_packet_size: a2, sync_offset: i3 });
              }, t3.prototype.bindDataSource = function(e4) {
                return e4.onDataArrival = this.parseChunks.bind(this), this;
              }, t3.prototype.resetMediaInfo = function() {
                this.media_info_ = new s.default();
              }, t3.prototype.parseChunks = function(e4, t4) {
                if (!(this.onError && this.onMediaInfo && this.onTrackMetadata && this.onDataAvailable)) throw new u.IllegalStateException("onError & onMediaInfo & onTrackMetadata & onDataAvailable callback must be specified");
                var i3 = 0;
                for (this.first_parse_ && (this.first_parse_ = false, i3 = this.sync_offset_); i3 + this.ts_packet_size_ <= e4.byteLength; ) {
                  var a2 = t4 + i3;
                  192 === this.ts_packet_size_ && (i3 += 4);
                  var n2 = new Uint8Array(e4, i3, 188), o2 = n2[0];
                  if (71 !== o2) {
                    r.default.e(this.TAG, "sync_byte = ".concat(o2, ", not 0x47"));
                    break;
                  }
                  var s2 = (64 & n2[1]) >>> 6, d2 = (n2[1], (31 & n2[1]) << 8 | n2[2]), _2 = (48 & n2[3]) >>> 4, l2 = 15 & n2[3], c2 = !(!this.pmt_ || this.pmt_.pcr_pid !== d2), h2 = {}, f2 = 4;
                  if (2 == _2 || 3 == _2) {
                    var p2 = n2[4];
                    if (p2 > 0 && (c2 || 3 == _2) && (h2.discontinuity_indicator = (128 & n2[5]) >>> 7, h2.random_access_indicator = (64 & n2[5]) >>> 6, h2.elementary_stream_priority_indicator = (32 & n2[5]) >>> 5, (16 & n2[5]) >>> 4)) {
                      var m2 = 300 * this.getPcrBase(n2) + ((1 & n2[10]) << 8 | n2[11]);
                      this.last_pcr_ = m2;
                    }
                    if (2 == _2 || 5 + p2 === 188) {
                      i3 += 188, 204 === this.ts_packet_size_ && (i3 += 16);
                      continue;
                    }
                    f2 = 5 + p2;
                  }
                  if (1 == _2 || 3 == _2) {
                    if (0 === d2 || d2 === this.current_pmt_pid_ || null != this.pmt_ && this.pmt_.pid_stream_type[d2] === L.kSCTE35) {
                      var g2 = 188 - f2;
                      this.handleSectionSlice(e4, i3 + f2, g2, { pid: d2, file_position: a2, payload_unit_start_indicator: s2, continuity_conunter: l2, random_access_indicator: h2.random_access_indicator });
                    } else if (null != this.pmt_ && null != this.pmt_.pid_stream_type[d2]) {
                      g2 = 188 - f2;
                      var v2 = this.pmt_.pid_stream_type[d2];
                      d2 !== this.pmt_.common_pids.h264 && d2 !== this.pmt_.common_pids.h265 && d2 !== this.pmt_.common_pids.av1 && d2 !== this.pmt_.common_pids.adts_aac && d2 !== this.pmt_.common_pids.loas_aac && d2 !== this.pmt_.common_pids.ac3 && d2 !== this.pmt_.common_pids.eac3 && d2 !== this.pmt_.common_pids.opus && d2 !== this.pmt_.common_pids.mp3 && true !== this.pmt_.pes_private_data_pids[d2] && true !== this.pmt_.timed_id3_pids[d2] && true !== this.pmt_.pgs_pids[d2] && true !== this.pmt_.synchronous_klv_pids[d2] && true !== this.pmt_.asynchronous_klv_pids[d2] || this.handlePESSlice(e4, i3 + f2, g2, { pid: d2, stream_type: v2, file_position: a2, payload_unit_start_indicator: s2, continuity_conunter: l2, random_access_indicator: h2.random_access_indicator });
                    }
                  }
                  i3 += 188, 204 === this.ts_packet_size_ && (i3 += 16);
                }
                return this.dispatchAudioVideoMediaSegment(), i3;
              }, t3.prototype.handleSectionSlice = function(e4, t4, i3, a2) {
                var n2 = new Uint8Array(e4, t4, i3), r2 = this.section_slice_queues_[a2.pid];
                if (a2.payload_unit_start_indicator) {
                  var o2 = n2[0];
                  if (null != r2 && 0 !== r2.total_length) {
                    var s2 = new Uint8Array(e4, t4 + 1, Math.min(i3, o2));
                    r2.slices.push(s2), r2.total_length += s2.byteLength, r2.total_length === r2.expected_length ? this.emitSectionSlices(r2, a2) : this.clearSlices(r2, a2);
                  }
                  for (var d2 = 1 + o2; d2 < n2.byteLength && 255 !== n2[d2 + 0]; ) {
                    var _2 = (15 & n2[d2 + 1]) << 8 | n2[d2 + 2];
                    this.section_slice_queues_[a2.pid] = new P(), (r2 = this.section_slice_queues_[a2.pid]).expected_length = _2 + 3, r2.file_position = a2.file_position, r2.random_access_indicator = a2.random_access_indicator, s2 = new Uint8Array(e4, t4 + d2, Math.min(i3 - d2, r2.expected_length - r2.total_length)), r2.slices.push(s2), r2.total_length += s2.byteLength, r2.total_length === r2.expected_length ? this.emitSectionSlices(r2, a2) : r2.total_length >= r2.expected_length && this.clearSlices(r2, a2), d2 += s2.byteLength;
                  }
                } else null != r2 && 0 !== r2.total_length && (s2 = new Uint8Array(e4, t4, Math.min(i3, r2.expected_length - r2.total_length)), r2.slices.push(s2), r2.total_length += s2.byteLength, r2.total_length === r2.expected_length ? this.emitSectionSlices(r2, a2) : r2.total_length >= r2.expected_length && this.clearSlices(r2, a2));
              }, t3.prototype.handlePESSlice = function(e4, t4, i3, a2) {
                var n2 = new Uint8Array(e4, t4, i3), o2 = n2[0] << 16 | n2[1] << 8 | n2[2], s2 = (n2[3], n2[4] << 8 | n2[5]);
                if (a2.payload_unit_start_indicator) {
                  if (1 !== o2) return void r.default.e(this.TAG, "handlePESSlice: packet_start_code_prefix should be 1 but with value ".concat(o2));
                  var d2 = this.pes_slice_queues_[a2.pid];
                  d2 && (0 === d2.expected_length || d2.expected_length === d2.total_length ? this.emitPESSlices(d2, a2) : this.clearSlices(d2, a2)), this.pes_slice_queues_[a2.pid] = new P(), this.pes_slice_queues_[a2.pid].file_position = a2.file_position, this.pes_slice_queues_[a2.pid].random_access_indicator = a2.random_access_indicator;
                }
                if (null != this.pes_slice_queues_[a2.pid]) {
                  var _2 = this.pes_slice_queues_[a2.pid];
                  _2.slices.push(n2), a2.payload_unit_start_indicator && (_2.expected_length = 0 === s2 ? 0 : s2 + 6), _2.total_length += n2.byteLength, _2.expected_length > 0 && _2.expected_length === _2.total_length ? this.emitPESSlices(_2, a2) : _2.expected_length > 0 && _2.expected_length < _2.total_length && this.clearSlices(_2, a2);
                }
              }, t3.prototype.emitSectionSlices = function(e4, t4) {
                for (var i3 = new Uint8Array(e4.total_length), a2 = 0, n2 = 0; a2 < e4.slices.length; a2++) {
                  var r2 = e4.slices[a2];
                  i3.set(r2, n2), n2 += r2.byteLength;
                }
                e4.slices = [], e4.expected_length = -1, e4.total_length = 0;
                var o2 = new O();
                o2.pid = t4.pid, o2.data = i3, o2.file_position = e4.file_position, o2.random_access_indicator = e4.random_access_indicator, this.parseSection(o2);
              }, t3.prototype.emitPESSlices = function(e4, t4) {
                for (var i3 = new Uint8Array(e4.total_length), a2 = 0, n2 = 0; a2 < e4.slices.length; a2++) {
                  var r2 = e4.slices[a2];
                  i3.set(r2, n2), n2 += r2.byteLength;
                }
                e4.slices = [], e4.expected_length = -1, e4.total_length = 0;
                var o2 = new B();
                o2.pid = t4.pid, o2.data = i3, o2.stream_type = t4.stream_type, o2.file_position = e4.file_position, o2.random_access_indicator = e4.random_access_indicator, this.parsePES(o2);
              }, t3.prototype.clearSlices = function(e4, t4) {
                e4.slices = [], e4.expected_length = -1, e4.total_length = 0;
              }, t3.prototype.parseSection = function(e4) {
                var t4 = e4.data, i3 = e4.pid;
                0 === i3 ? this.parsePAT(t4) : i3 === this.current_pmt_pid_ ? this.parsePMT(t4) : null != this.pmt_ && this.pmt_.scte_35_pids[i3] && this.parseSCTE35(t4);
              }, t3.prototype.parsePES = function(e4) {
                var t4 = e4.data, i3 = t4[0] << 16 | t4[1] << 8 | t4[2], a2 = t4[3], n2 = t4[4] << 8 | t4[5];
                if (1 === i3) if (188 !== a2 && 190 !== a2 && 191 !== a2 && 240 !== a2 && 241 !== a2 && 255 !== a2 && 242 !== a2 && 248 !== a2) {
                  t4[6];
                  var o2 = (192 & t4[7]) >>> 6, s2 = t4[8], d2 = void 0, _2 = void 0;
                  2 !== o2 && 3 !== o2 || (d2 = this.getTimestamp(t4, 9), _2 = 3 === o2 ? this.getTimestamp(t4, 14) : d2);
                  var l2 = 9 + s2, u2 = void 0;
                  if (0 !== n2) {
                    if (n2 < 3 + s2) return void r.default.v(this.TAG, "Malformed PES: PES_packet_length < 3 + PES_header_data_length");
                    u2 = n2 - 3 - s2;
                  } else u2 = t4.byteLength - l2;
                  var c2 = t4.subarray(l2, l2 + u2);
                  switch (e4.stream_type) {
                    case L.kMPEG1Audio:
                    case L.kMPEG2Audio:
                      this.parseMP3Payload(c2, d2);
                      break;
                    case L.kPESPrivateData:
                      this.pmt_.common_pids.av1 === e4.pid ? this.parseAV1Payload(c2, d2, _2, e4.file_position, e4.random_access_indicator) : this.pmt_.common_pids.opus === e4.pid ? this.parseOpusPayload(c2, d2) : this.pmt_.common_pids.ac3 === e4.pid ? this.parseAC3Payload(c2, d2) : this.pmt_.common_pids.eac3 === e4.pid ? this.parseEAC3Payload(c2, d2) : this.pmt_.asynchronous_klv_pids[e4.pid] ? this.parseAsynchronousKLVMetadataPayload(c2, e4.pid, a2) : this.pmt_.smpte2038_pids[e4.pid] ? this.parseSMPTE2038MetadataPayload(c2, d2, _2, e4.pid, a2) : this.parsePESPrivateDataPayload(c2, d2, _2, e4.pid, a2);
                      break;
                    case L.kADTSAAC:
                      this.parseADTSAACPayload(c2, d2);
                      break;
                    case L.kLOASAAC:
                      this.parseLOASAACPayload(c2, d2);
                      break;
                    case L.kAC3:
                      this.parseAC3Payload(c2, d2);
                      break;
                    case L.kEAC3:
                      this.parseEAC3Payload(c2, d2);
                      break;
                    case L.kMetadata:
                      this.pmt_.timed_id3_pids[e4.pid] ? this.parseTimedID3MetadataPayload(c2, d2, _2, e4.pid, a2) : this.pmt_.synchronous_klv_pids[e4.pid] && this.parseSynchronousKLVMetadataPayload(c2, d2, _2, e4.pid, a2);
                      break;
                    case L.kPGS:
                      this.parsePGSPayload(c2, d2, _2, e4.pid, a2, this.pmt_.pgs_langs[e4.pid]);
                      break;
                    case L.kH264:
                      this.parseH264Payload(c2, d2, _2, e4.file_position, e4.random_access_indicator);
                      break;
                    case L.kH265:
                      this.parseH265Payload(c2, d2, _2, e4.file_position, e4.random_access_indicator);
                  }
                } else 188 !== a2 && 191 !== a2 && 240 !== a2 && 241 !== a2 && 255 !== a2 && 242 !== a2 && 248 !== a2 || e4.stream_type !== L.kPESPrivateData || (l2 = 6, u2 = void 0, u2 = 0 !== n2 ? n2 : t4.byteLength - l2, c2 = t4.subarray(l2, l2 + u2), this.parsePESPrivateDataPayload(c2, void 0, void 0, e4.pid, a2));
                else r.default.e(this.TAG, "parsePES: packet_start_code_prefix should be 1 but with value ".concat(i3));
              }, t3.prototype.parsePAT = function(e4) {
                var t4 = e4[0];
                if (0 === t4) {
                  var i3 = (15 & e4[1]) << 8 | e4[2], a2 = (e4[3], e4[4], (62 & e4[5]) >>> 1), n2 = 1 & e4[5], o2 = e4[6], s2 = (e4[7], null);
                  if (1 === n2 && 0 === o2) (s2 = new C()).version_number = a2;
                  else if (null == (s2 = this.pat_)) return;
                  for (var d2 = i3 - 5 - 4, _2 = -1, l2 = -1, u2 = 8; u2 < 8 + d2; u2 += 4) {
                    var c2 = e4[u2] << 8 | e4[u2 + 1], h2 = (31 & e4[u2 + 2]) << 8 | e4[u2 + 3];
                    0 === c2 ? s2.network_pid = h2 : (s2.program_pmt_pid[c2] = h2, -1 === _2 && (_2 = c2), -1 === l2 && (l2 = h2));
                  }
                  1 === n2 && 0 === o2 && (null == this.pat_ && r.default.v(this.TAG, "Parsed first PAT: ".concat(JSON.stringify(s2))), this.pat_ = s2, this.current_program_ = _2, this.current_pmt_pid_ = l2);
                } else r.default.e(this.TAG, "parsePAT: table_id ".concat(t4, " is not corresponded to PAT!"));
              }, t3.prototype.parsePMT = function(e4) {
                var t4 = e4[0];
                if (2 === t4) {
                  var i3 = (15 & e4[1]) << 8 | e4[2], a2 = e4[3] << 8 | e4[4], n2 = (62 & e4[5]) >>> 1, o2 = 1 & e4[5], s2 = e4[6], d2 = (e4[7], null);
                  if (1 === o2 && 0 === s2) (d2 = new I()).program_number = a2, d2.version_number = n2, this.program_pmt_map_[a2] = d2;
                  else if (null == (d2 = this.program_pmt_map_[a2])) return;
                  d2.pcr_pid = (31 & e4[8]) << 8 | e4[9];
                  for (var _2 = (15 & e4[10]) << 8 | e4[11], l2 = 12 + _2, u2 = i3 - 9 - _2 - 4, c2 = l2; c2 < l2 + u2; ) {
                    var h2 = e4[c2], f2 = (31 & e4[c2 + 1]) << 8 | e4[c2 + 2], p2 = (15 & e4[c2 + 3]) << 8 | e4[c2 + 4];
                    d2.pid_stream_type[f2] = h2;
                    var m2 = d2.common_pids.h264 || d2.common_pids.h265, g2 = d2.common_pids.adts_aac || d2.common_pids.loas_aac || d2.common_pids.ac3 || d2.common_pids.eac3 || d2.common_pids.opus || d2.common_pids.mp3;
                    if (h2 !== L.kH264 || m2) if (h2 !== L.kH265 || m2) if (h2 !== L.kADTSAAC || g2) if (h2 !== L.kLOASAAC || g2) if (h2 !== L.kAC3 || g2) if (h2 !== L.kEAC3 || g2) if (h2 !== L.kMPEG1Audio && h2 !== L.kMPEG2Audio || g2) {
                      if (h2 === L.kPESPrivateData) {
                        if (d2.pes_private_data_pids[f2] = true, p2 > 0) {
                          for (var v2 = c2 + 5; v2 < c2 + 5 + p2; ) {
                            var y2 = e4[v2 + 0], S2 = e4[v2 + 1];
                            if (5 === y2) {
                              var E2 = String.fromCharCode.apply(String, Array.from(e4.subarray(v2 + 2, v2 + 2 + S2)));
                              "VANC" === E2 ? d2.smpte2038_pids[f2] = true : "AV01" === E2 ? d2.common_pids.av1 = f2 : "Opus" === E2 ? d2.common_pids.opus = f2 : "KLVA" === E2 && (d2.asynchronous_klv_pids[f2] = true);
                            } else if (127 === y2) {
                              if (f2 === d2.common_pids.opus) {
                                var A2 = null;
                                if (128 === e4[v2 + 2] && (A2 = e4[v2 + 3]), null == A2) {
                                  r.default.e(this.TAG, "Not Supported Opus channel count.");
                                  continue;
                                }
                                var b2 = { codec: "opus", channel_count: 15 & A2 ? 15 & A2 : 2, channel_config_code: A2, sample_rate: 48e3 }, R2 = { codec: "opus", meta: b2 };
                                0 == this.audio_init_segment_dispatched_ ? (this.audio_metadata_ = b2, this.dispatchAudioInitSegment(R2)) : this.detectAudioMetadataChange(R2) && (this.dispatchAudioMediaSegment(), this.dispatchAudioInitSegment(R2));
                              }
                            } else 128 === y2 && f2 === d2.common_pids.av1 && (this.video_metadata_.av1c = e4.subarray(v2 + 2, v2 + 2 + S2));
                            v2 += 2 + S2;
                          }
                          var T2 = e4.subarray(c2 + 5, c2 + 5 + p2);
                          this.dispatchPESPrivateDataDescriptor(f2, h2, T2);
                        }
                      } else if (h2 === L.kMetadata) {
                        if (p2 > 0) for (v2 = c2 + 5; v2 < c2 + 5 + p2; ) {
                          y2 = e4[v2 + 0];
                          var k2 = e4[v2 + 1];
                          if (38 === y2) {
                            var D2 = e4[v2 + 2] << 8 | e4[v2 + 3], M2 = null;
                            65535 === D2 && (M2 = String.fromCharCode.apply(String, Array.from(e4.subarray(v2 + 4, v2 + 4 + 4))));
                            var C2 = null;
                            if (255 === e4[v2 + 4 + (65535 === D2 ? 4 : 0)]) {
                              var w2 = 4 + (65535 === D2 ? 4 : 0) + 1;
                              C2 = String.fromCharCode.apply(String, Array.from(e4.subarray(v2 + w2, v2 + w2 + 4)));
                            }
                            "ID3 " === M2 && "ID3 " === C2 ? d2.timed_id3_pids[f2] = true : "KLVA" === C2 && (d2.synchronous_klv_pids[f2] = true);
                          }
                          v2 += 2 + k2;
                        }
                      } else if (h2 === L.kSCTE35) d2.scte_35_pids[f2] = true;
                      else if (h2 === L.kPGS) {
                        if (d2.pgs_langs[f2] = "und", p2 > 0) for (v2 = c2 + 5; v2 < c2 + 5 + p2; ) {
                          y2 = e4[v2 + 0];
                          var B2 = e4[v2 + 1];
                          if (10 === y2) {
                            var O2 = String.fromCharCode.apply(String, Array.from(e4.slice(v2 + 2, v2 + 5)));
                            d2.pgs_langs[f2] = O2;
                          }
                          v2 += 2 + B2;
                        }
                        d2.pgs_pids[f2] = true;
                      }
                    } else d2.common_pids.mp3 = f2;
                    else d2.common_pids.eac3 = f2;
                    else d2.common_pids.ac3 = f2;
                    else d2.common_pids.loas_aac = f2;
                    else d2.common_pids.adts_aac = f2;
                    else d2.common_pids.h265 = f2;
                    else d2.common_pids.h264 = f2;
                    c2 += 5 + p2;
                  }
                  a2 === this.current_program_ && (null == this.pmt_ && r.default.v(this.TAG, "Parsed first PMT: ".concat(JSON.stringify(d2))), this.pmt_ = d2, (d2.common_pids.h264 || d2.common_pids.h265 || d2.common_pids.av1) && (this.has_video_ = true), (d2.common_pids.adts_aac || d2.common_pids.loas_aac || d2.common_pids.ac3 || d2.common_pids.opus || d2.common_pids.mp3) && (this.has_audio_ = true));
                } else r.default.e(this.TAG, "parsePMT: table_id ".concat(t4, " is not corresponded to PMT!"));
              }, t3.prototype.parseSCTE35 = function(e4) {
                var t4 = (function(e5) {
                  var t5 = new f(e5), i4 = t5.readBits(8), a2 = t5.readBool(), n2 = t5.readBool();
                  t5.readBits(2);
                  var r2 = t5.readBits(12), o2 = t5.readBits(8), s2 = t5.readBool(), d2 = t5.readBits(6), _2 = 4 * t5.readBits(31) + t5.readBits(2), l2 = t5.readBits(8), u2 = t5.readBits(12), c2 = t5.readBits(12), h2 = t5.readBits(8), p2 = null;
                  h2 === z.kSpliceNull ? p2 = {} : h2 === z.kSpliceSchedule ? p2 = (function(e6) {
                    for (var t6 = e6.readBits(8), i5 = [], a3 = 0; a3 < t6; a3++) i5.push(ne(e6));
                    return { splice_count: t6, events: i5 };
                  })(t5) : h2 === z.kSpliceInsert ? p2 = (function(e6) {
                    var t6 = e6.readBits(32), i5 = e6.readBool();
                    e6.readBits(7);
                    var a3 = { splice_event_id: t6, splice_event_cancel_indicator: i5 };
                    if (i5) return a3;
                    if (a3.out_of_network_indicator = e6.readBool(), a3.program_splice_flag = e6.readBool(), a3.duration_flag = e6.readBool(), a3.splice_immediate_flag = e6.readBool(), e6.readBits(4), a3.program_splice_flag && !a3.splice_immediate_flag && (a3.splice_time = ee(e6)), !a3.program_splice_flag) {
                      a3.component_count = e6.readBits(8), a3.components = [];
                      for (var n3 = 0; n3 < a3.component_count; n3++) a3.components.push(ie(a3.splice_immediate_flag, e6));
                    }
                    return a3.duration_flag && (a3.break_duration = te(e6)), a3.unique_program_id = e6.readBits(16), a3.avail_num = e6.readBits(8), a3.avails_expected = e6.readBits(8), a3;
                  })(t5) : h2 === z.kTimeSignal ? p2 = (function(e6) {
                    return { splice_time: ee(e6) };
                  })(t5) : h2 === z.kBandwidthReservation ? p2 = {} : h2 === z.kPrivateCommand ? p2 = (function(e6, t6) {
                    for (var i5 = String.fromCharCode(t6.readBits(8), t6.readBits(8), t6.readBits(8), t6.readBits(8)), a3 = new Uint8Array(e6 - 4), n3 = 0; n3 < e6 - 4; n3++) a3[n3] = t6.readBits(8);
                    return { identifier: i5, private_data: a3.buffer };
                  })(c2, t5) : t5.readBits(8 * c2);
                  for (var m2 = [], g2 = t5.readBits(16), v2 = 0; v2 < g2; ) {
                    var y2 = t5.readBits(8), S2 = t5.readBits(8), E2 = String.fromCharCode(t5.readBits(8), t5.readBits(8), t5.readBits(8), t5.readBits(8));
                    0 === y2 ? m2.push(re(y2, S2, E2, t5)) : 1 === y2 ? m2.push(oe(y2, S2, E2, t5)) : 2 === y2 ? m2.push(de(y2, S2, E2, t5)) : 3 === y2 ? m2.push(_e(y2, S2, E2, t5)) : 4 === y2 ? m2.push(ue(y2, S2, E2, t5)) : t5.readBits(8 * (S2 - 4)), v2 += 2 + S2;
                  }
                  var A2 = { table_id: i4, section_syntax_indicator: a2, private_indicator: n2, section_length: r2, protocol_version: o2, encrypted_packet: s2, encryption_algorithm: d2, pts_adjustment: _2, cw_index: l2, tier: u2, splice_command_length: c2, splice_command_type: h2, splice_command: p2, descriptor_loop_length: g2, splice_descriptors: m2, E_CRC32: s2 ? t5.readBits(32) : void 0, CRC32: t5.readBits(32) };
                  if (h2 === z.kSpliceInsert) {
                    var b2 = p2;
                    if (b2.splice_event_cancel_indicator) return { splice_command_type: h2, detail: A2, data: e5 };
                    if (b2.program_splice_flag && !b2.splice_immediate_flag) {
                      var R2 = b2.duration_flag ? b2.break_duration.auto_return : void 0, T2 = b2.duration_flag ? b2.break_duration.duration / 90 : void 0;
                      return b2.splice_time.time_specified_flag ? { splice_command_type: h2, pts: (_2 + b2.splice_time.pts_time) % Math.pow(2, 33), auto_return: R2, duraiton: T2, detail: A2, data: e5 } : { splice_command_type: h2, auto_return: R2, duraiton: T2, detail: A2, data: e5 };
                    }
                    return { splice_command_type: h2, auto_return: R2 = b2.duration_flag ? b2.break_duration.auto_return : void 0, duraiton: T2 = b2.duration_flag ? b2.break_duration.duration / 90 : void 0, detail: A2, data: e5 };
                  }
                  if (h2 === z.kTimeSignal) {
                    var L2 = p2;
                    return L2.splice_time.time_specified_flag ? { splice_command_type: h2, pts: (_2 + L2.splice_time.pts_time) % Math.pow(2, 33), detail: A2, data: e5 } : { splice_command_type: h2, detail: A2, data: e5 };
                  }
                  return { splice_command_type: h2, detail: A2, data: e5 };
                })(e4);
                if (null != t4.pts) {
                  var i3 = Math.floor(t4.pts / this.timescale_);
                  t4.pts = i3;
                } else t4.nearest_pts = this.getNearestTimestampMilliseconds();
                this.onSCTE35Metadata && this.onSCTE35Metadata(t4);
              }, t3.prototype.parseAV1Payload = function(e4, t4, i3, a2, n2) {
                for (var r2 = new Le(e4), o2 = null, s2 = [], d2 = 0, _2 = false, l2 = null; null != (o2 = r2.readNextOBUPayload()); ) {
                  if ((l2 = b.parseOBUs(o2, this.video_metadata_.details)) && true === l2.keyframe) if (this.video_init_segment_dispatched_) {
                    if (true === this.detectVideoMetadataChange(null, l2)) {
                      var u2;
                      this.video_metadata_changed_ = true, this.dispatchVideoMediaSegment(), (u2 = new Uint8Array(new ArrayBuffer(this.video_metadata_.av1c.byteLength + l2.sequence_header_data.byteLength))).set(this.video_metadata_.av1c, 0), u2.set(l2.sequence_header_data, this.video_metadata_.av1c.byteLength), l2.av1c = u2, this.dispatchVideoInitSegment();
                    }
                  } else (u2 = new Uint8Array(new ArrayBuffer(this.video_metadata_.av1c.byteLength + l2.sequence_header_data.byteLength))).set(this.video_metadata_.av1c, 0), u2.set(l2.sequence_header_data, this.video_metadata_.av1c.byteLength), l2.av1c = u2, this.video_metadata_.details = l2, this.dispatchVideoInitSegment();
                  this.video_metadata_.details = l2, _2 || (_2 = l2.keyframe), s2.push({ data: o2 }), d2 += o2.byteLength;
                }
                var c2 = Math.floor(t4 / this.timescale_), h2 = Math.floor(i3 / this.timescale_);
                if (s2.length) {
                  var f2 = this.video_track_, p2 = { units: s2, length: d2, isKeyframe: _2, dts: h2, pts: c2, cts: c2 - h2, file_position: a2 };
                  f2.samples.push(p2), f2.length += d2;
                }
              }, t3.prototype.parseH264Payload = function(e4, t4, i3, a2, n2) {
                for (var o2 = new G(e4), s2 = null, d2 = [], _2 = 0, l2 = false; null != (s2 = o2.readNextNaluPayload()); ) {
                  var u2 = new V(s2);
                  if (u2.type === w.kSliceSPS) {
                    var c2 = p.parseSPS(s2.data);
                    this.video_init_segment_dispatched_ ? true === this.detectVideoMetadataChange(u2, c2) && (r.default.v(this.TAG, "H264: Critical h264 metadata has been changed, attempt to re-generate InitSegment"), this.video_metadata_changed_ = true, this.video_metadata_ = { vps: void 0, sps: u2, pps: void 0, av1c: void 0, details: c2 }) : (this.video_metadata_.sps = u2, this.video_metadata_.details = c2);
                  } else u2.type === w.kSlicePPS ? this.video_init_segment_dispatched_ && !this.video_metadata_changed_ || (this.video_metadata_.pps = u2, this.video_metadata_.sps && this.video_metadata_.pps && (this.video_metadata_changed_ && this.dispatchVideoMediaSegment(), this.dispatchVideoInitSegment())) : u2.type === w.kSliceIDR || u2.type === w.kSliceNonIDR && 1 === n2 ? l2 = true : u2.type === w.kSliceSEI && this.parseSEIPayload(s2.data, t4, "h264");
                  this.video_init_segment_dispatched_ && (d2.push(u2), _2 += u2.data.byteLength);
                }
                var h2 = Math.floor(t4 / this.timescale_), f2 = Math.floor(i3 / this.timescale_);
                if (d2.length) {
                  var m2 = this.video_track_, g2 = { units: d2, length: _2, isKeyframe: l2, dts: f2, pts: h2, cts: h2 - f2, file_position: a2 };
                  m2.samples.push(g2), m2.length += _2;
                }
              }, t3.prototype.parseH265Payload = function(e4, t4, i3, a2, n2) {
                for (var o2 = new fe(e4), s2 = null, d2 = [], _2 = 0, l2 = false; null != (s2 = o2.readNextNaluPayload()); ) {
                  var u2 = new he(s2);
                  if (u2.type === $.kSliceVPS) {
                    if (!this.video_init_segment_dispatched_) {
                      var c2 = g.parseVPS(s2.data);
                      this.video_metadata_.vps = u2, this.video_metadata_.details = Me(Me({}, this.video_metadata_.details), c2);
                    }
                  } else u2.type === $.kSliceSPS ? (c2 = g.parseSPS(s2.data), this.video_init_segment_dispatched_ ? true === this.detectVideoMetadataChange(u2, c2) && (r.default.v(this.TAG, "H265: Critical h265 metadata has been changed, attempt to re-generate InitSegment"), this.video_metadata_changed_ = true, this.video_metadata_ = { vps: void 0, sps: u2, pps: void 0, av1c: void 0, details: c2 }) : (this.video_metadata_.sps = u2, this.video_metadata_.details = Me(Me({}, this.video_metadata_.details), c2))) : u2.type === $.kSlicePPS ? this.video_init_segment_dispatched_ && !this.video_metadata_changed_ || (c2 = g.parsePPS(s2.data), this.video_metadata_.pps = u2, this.video_metadata_.details = Me(Me({}, this.video_metadata_.details), c2), this.video_metadata_.vps && this.video_metadata_.sps && this.video_metadata_.pps && (this.video_metadata_changed_ && this.dispatchVideoMediaSegment(), this.dispatchVideoInitSegment())) : u2.type === $.kSliceIDR_W_RADL || u2.type === $.kSliceIDR_N_LP || u2.type === $.kSliceCRA_NUT ? l2 = true : u2.type !== $.kSliceSEI && u2.type !== $.kSliceSEISuffix || this.parseSEIPayload(s2.data, t4, "h265");
                  this.video_init_segment_dispatched_ && (d2.push(u2), _2 += u2.data.byteLength);
                }
                var h2 = Math.floor(t4 / this.timescale_), f2 = Math.floor(i3 / this.timescale_);
                if (d2.length) {
                  var p2 = this.video_track_, m2 = { units: d2, length: _2, isKeyframe: l2, dts: f2, pts: h2, cts: h2 - f2, file_position: a2 };
                  p2.samples.push(m2), p2.length += _2;
                }
              }, t3.prototype.detectVideoMetadataChange = function(e4, t4) {
                if (t4.codec_mimetype !== this.video_metadata_.details.codec_mimetype) return r.default.v(this.TAG, "Video: Codec mimeType changed from " + "".concat(this.video_metadata_.details.codec_mimetype, " to ").concat(t4.codec_mimetype)), true;
                if (t4.codec_size.width !== this.video_metadata_.details.codec_size.width || t4.codec_size.height !== this.video_metadata_.details.codec_size.height) {
                  var i3 = this.video_metadata_.details.codec_size, a2 = t4.codec_size;
                  return r.default.v(this.TAG, "Video: Coded Resolution changed from " + "".concat(i3.width, "x").concat(i3.height, " to ").concat(a2.width, "x").concat(a2.height)), true;
                }
                return t4.present_size.width !== this.video_metadata_.details.present_size.width && (r.default.v(this.TAG, "Video: Present resolution width changed from " + "".concat(this.video_metadata_.details.present_size.width, " to ").concat(t4.present_size.width)), true);
              }, t3.prototype.isInitSegmentDispatched = function() {
                return this.has_video_ && this.has_audio_ ? this.video_init_segment_dispatched_ && this.audio_init_segment_dispatched_ : this.has_video_ && !this.has_audio_ ? this.video_init_segment_dispatched_ : !(this.has_video_ || !this.has_audio_) && this.audio_init_segment_dispatched_;
              }, t3.prototype.dispatchVideoInitSegment = function() {
                var e4 = this.video_metadata_.details, t4 = { type: "video" };
                t4.id = this.video_track_.id, t4.timescale = 1e3, t4.duration = this.duration_, t4.codecWidth = e4.codec_size.width, t4.codecHeight = e4.codec_size.height, t4.presentWidth = e4.present_size.width, t4.presentHeight = e4.present_size.height, t4.profile = e4.profile_string, t4.level = e4.level_string, t4.bitDepth = e4.bit_depth, t4.chromaFormat = e4.chroma_format, t4.sarRatio = e4.sar_ratio, t4.frameRate = e4.frame_rate;
                var i3 = t4.frameRate.fps_den, a2 = t4.frameRate.fps_num;
                if (t4.refSampleDuration = i3 / a2 * 1e3, t4.codec = e4.codec_mimetype, this.video_metadata_.av1c) t4.av1c = this.video_metadata_.av1c, 0 == this.video_init_segment_dispatched_ && r.default.v(this.TAG, "Generated first AV1 for mimeType: ".concat(t4.codec));
                else if (this.video_metadata_.vps) {
                  var n2 = this.video_metadata_.vps.data.subarray(4), o2 = this.video_metadata_.sps.data.subarray(4), s2 = this.video_metadata_.pps.data.subarray(4), d2 = new pe(n2, o2, s2, e4);
                  t4.hvcc = d2.getData(), 0 == this.video_init_segment_dispatched_ && r.default.v(this.TAG, "Generated first HEVCDecoderConfigurationRecord for mimeType: ".concat(t4.codec));
                } else {
                  o2 = this.video_metadata_.sps.data.subarray(4), s2 = this.video_metadata_.pps.data.subarray(4);
                  var _2 = new F(o2, s2, e4);
                  t4.avcc = _2.getData(), 0 == this.video_init_segment_dispatched_ && r.default.v(this.TAG, "Generated first AVCDecoderConfigurationRecord for mimeType: ".concat(t4.codec));
                }
                this.onTrackMetadata("video", t4), this.video_init_segment_dispatched_ = true, this.video_metadata_changed_ = false;
                var l2 = this.media_info_;
                l2.hasVideo = true, l2.width = t4.codecWidth, l2.height = t4.codecHeight, l2.fps = t4.frameRate.fps, l2.profile = t4.profile, l2.level = t4.level, l2.refFrames = e4.ref_frames, l2.chromaFormat = e4.chroma_format_string, l2.sarNum = t4.sarRatio.width, l2.sarDen = t4.sarRatio.height, l2.videoCodec = t4.codec, l2.hasAudio && l2.audioCodec ? l2.mimeType = 'video/mp2t; codecs="'.concat(l2.videoCodec, ",").concat(l2.audioCodec, '"') : l2.mimeType = 'video/mp2t; codecs="'.concat(l2.videoCodec, '"'), l2.isComplete() && this.onMediaInfo(l2);
              }, t3.prototype.dispatchVideoMediaSegment = function() {
                this.isInitSegmentDispatched() && this.video_track_.length && this.onDataAvailable(null, this.video_track_);
              }, t3.prototype.dispatchAudioMediaSegment = function() {
                this.isInitSegmentDispatched() && this.audio_track_.length && this.onDataAvailable(this.audio_track_, null);
              }, t3.prototype.dispatchAudioVideoMediaSegment = function() {
                this.isInitSegmentDispatched() && (this.audio_track_.length || this.video_track_.length) && this.onDataAvailable(this.audio_track_, this.video_track_);
              }, t3.prototype.parseADTSAACPayload = function(e4, t4) {
                if (!this.has_video_ || this.video_init_segment_dispatched_) {
                  if (this.aac_last_incomplete_data_) {
                    var i3 = new Uint8Array(e4.byteLength + this.aac_last_incomplete_data_.byteLength);
                    i3.set(this.aac_last_incomplete_data_, 0), i3.set(e4, this.aac_last_incomplete_data_.byteLength), e4 = i3;
                  }
                  var a2, n2;
                  if (null != t4 && (n2 = t4 / this.timescale_), "aac" === this.audio_metadata_.codec) {
                    if (null == t4 && null != this.audio_last_sample_pts_) a2 = 1024 / this.audio_metadata_.sampling_frequency * 1e3, n2 = this.audio_last_sample_pts_ + a2;
                    else if (null == t4) return void r.default.w(this.TAG, "AAC: Unknown pts");
                    if (this.aac_last_incomplete_data_ && this.audio_last_sample_pts_) {
                      a2 = 1024 / this.audio_metadata_.sampling_frequency * 1e3;
                      var o2 = this.audio_last_sample_pts_ + a2;
                      Math.abs(o2 - n2) > 1 && (r.default.w(this.TAG, "AAC: Detected pts overlapped, " + "expected: ".concat(o2, "ms, PES pts: ").concat(n2, "ms")), n2 = o2);
                    }
                  }
                  for (var s2, d2 = new Y(e4), _2 = null, l2 = n2; null != (_2 = d2.readNextAACFrame()); ) {
                    a2 = 1024 / _2.sampling_frequency * 1e3;
                    var u2 = { codec: "aac", data: _2 };
                    0 == this.audio_init_segment_dispatched_ ? (this.audio_metadata_ = { codec: "aac", audio_object_type: _2.audio_object_type, sampling_freq_index: _2.sampling_freq_index, sampling_frequency: _2.sampling_frequency, channel_config: _2.channel_config }, this.dispatchAudioInitSegment(u2)) : this.detectAudioMetadataChange(u2) && (this.dispatchAudioMediaSegment(), this.dispatchAudioInitSegment(u2)), s2 = l2;
                    var c2 = Math.floor(l2), h2 = { unit: _2.data, length: _2.data.byteLength, pts: c2, dts: c2 };
                    this.audio_track_.samples.push(h2), this.audio_track_.length += _2.data.byteLength, l2 += a2;
                  }
                  d2.hasIncompleteData() && (this.aac_last_incomplete_data_ = d2.getIncompleteData()), s2 && (this.audio_last_sample_pts_ = s2);
                }
              }, t3.prototype.parseLOASAACPayload = function(e4, t4) {
                var i3;
                if (!this.has_video_ || this.video_init_segment_dispatched_) {
                  if (this.aac_last_incomplete_data_) {
                    var a2 = new Uint8Array(e4.byteLength + this.aac_last_incomplete_data_.byteLength);
                    a2.set(this.aac_last_incomplete_data_, 0), a2.set(e4, this.aac_last_incomplete_data_.byteLength), e4 = a2;
                  }
                  var n2, o2;
                  if (null != t4 && (o2 = t4 / this.timescale_), "aac" === this.audio_metadata_.codec) {
                    if (null == t4 && null != this.audio_last_sample_pts_) n2 = 1024 / this.audio_metadata_.sampling_frequency * 1e3, o2 = this.audio_last_sample_pts_ + n2;
                    else if (null == t4) return void r.default.w(this.TAG, "AAC: Unknown pts");
                    if (this.aac_last_incomplete_data_ && this.audio_last_sample_pts_) {
                      n2 = 1024 / this.audio_metadata_.sampling_frequency * 1e3;
                      var s2 = this.audio_last_sample_pts_ + n2;
                      Math.abs(s2 - o2) > 1 && (r.default.w(this.TAG, "AAC: Detected pts overlapped, " + "expected: ".concat(s2, "ms, PES pts: ").concat(o2, "ms")), o2 = s2);
                    }
                  }
                  for (var d2, _2 = new X(e4), l2 = null, u2 = o2; null != (l2 = _2.readNextAACFrame(null !== (i3 = this.loas_previous_frame) && void 0 !== i3 ? i3 : void 0)); ) {
                    this.loas_previous_frame = l2, n2 = 1024 / l2.sampling_frequency * 1e3;
                    var c2 = { codec: "aac", data: l2 };
                    0 == this.audio_init_segment_dispatched_ ? (this.audio_metadata_ = { codec: "aac", audio_object_type: l2.audio_object_type, sampling_freq_index: l2.sampling_freq_index, sampling_frequency: l2.sampling_frequency, channel_config: l2.channel_config }, this.dispatchAudioInitSegment(c2)) : this.detectAudioMetadataChange(c2) && (this.dispatchAudioMediaSegment(), this.dispatchAudioInitSegment(c2)), d2 = u2;
                    var h2 = Math.floor(u2), f2 = { unit: l2.data, length: l2.data.byteLength, pts: h2, dts: h2 };
                    this.audio_track_.samples.push(f2), this.audio_track_.length += l2.data.byteLength, u2 += n2;
                  }
                  _2.hasIncompleteData() && (this.aac_last_incomplete_data_ = _2.getIncompleteData()), d2 && (this.audio_last_sample_pts_ = d2);
                }
              }, t3.prototype.parseAC3Payload = function(e4, t4) {
                if (!this.has_video_ || this.video_init_segment_dispatched_) {
                  var i3, a2;
                  if (null != t4 && (a2 = t4 / this.timescale_), "ac-3" === this.audio_metadata_.codec) {
                    if (null == t4 && null != this.audio_last_sample_pts_) i3 = 1536 / this.audio_metadata_.sampling_frequency * 1e3, a2 = this.audio_last_sample_pts_ + i3;
                    else if (null == t4) return void r.default.w(this.TAG, "AC3: Unknown pts");
                  }
                  for (var n2, o2 = new Se(e4), s2 = null, d2 = a2; null != (s2 = o2.readNextAC3Frame()); ) {
                    i3 = 1536 / s2.sampling_frequency * 1e3;
                    var _2 = { codec: "ac-3", data: s2 };
                    0 == this.audio_init_segment_dispatched_ ? (this.audio_metadata_ = { codec: "ac-3", sampling_frequency: s2.sampling_frequency, bit_stream_identification: s2.bit_stream_identification, bit_stream_mode: s2.bit_stream_mode, low_frequency_effects_channel_on: s2.low_frequency_effects_channel_on, channel_mode: s2.channel_mode }, this.dispatchAudioInitSegment(_2)) : this.detectAudioMetadataChange(_2) && (this.dispatchAudioMediaSegment(), this.dispatchAudioInitSegment(_2)), n2 = d2;
                    var l2 = Math.floor(d2), u2 = { unit: s2.data, length: s2.data.byteLength, pts: l2, dts: l2 };
                    this.audio_track_.samples.push(u2), this.audio_track_.length += s2.data.byteLength, d2 += i3;
                  }
                  n2 && (this.audio_last_sample_pts_ = n2);
                }
              }, t3.prototype.parseEAC3Payload = function(e4, t4) {
                if (!this.has_video_ || this.video_init_segment_dispatched_) {
                  var i3, a2;
                  if (null != t4 && (a2 = t4 / this.timescale_), "ec-3" === this.audio_metadata_.codec) {
                    if (null == t4 && null != this.audio_last_sample_pts_) i3 = 256 * this.audio_metadata_.num_blks / this.audio_metadata_.sampling_frequency * 1e3, a2 = this.audio_last_sample_pts_ + i3;
                    else if (null == t4) return void r.default.w(this.TAG, "EAC3: Unknown pts");
                  }
                  for (var n2, o2 = new be(e4), s2 = null, d2 = a2; null != (s2 = o2.readNextEAC3Frame()); ) {
                    i3 = 1536 / s2.sampling_frequency * 1e3;
                    var _2 = { codec: "ec-3", data: s2 };
                    0 == this.audio_init_segment_dispatched_ ? (this.audio_metadata_ = { codec: "ec-3", sampling_frequency: s2.sampling_frequency, bit_stream_identification: s2.bit_stream_identification, low_frequency_effects_channel_on: s2.low_frequency_effects_channel_on, num_blks: s2.num_blks, channel_mode: s2.channel_mode }, this.dispatchAudioInitSegment(_2)) : this.detectAudioMetadataChange(_2) && (this.dispatchAudioMediaSegment(), this.dispatchAudioInitSegment(_2)), n2 = d2;
                    var l2 = Math.floor(d2), u2 = { unit: s2.data, length: s2.data.byteLength, pts: l2, dts: l2 };
                    this.audio_track_.samples.push(u2), this.audio_track_.length += s2.data.byteLength, d2 += i3;
                  }
                  n2 && (this.audio_last_sample_pts_ = n2);
                }
              }, t3.prototype.parseOpusPayload = function(e4, t4) {
                if (!this.has_video_ || this.video_init_segment_dispatched_) {
                  var i3, a2;
                  if (null != t4 && (a2 = t4 / this.timescale_), "opus" === this.audio_metadata_.codec) {
                    if (null == t4 && null != this.audio_last_sample_pts_) i3 = 20, a2 = this.audio_last_sample_pts_ + i3;
                    else if (null == t4) return void r.default.w(this.TAG, "Opus: Unknown pts");
                  }
                  for (var n2, o2 = a2, s2 = 0; s2 < e4.length; ) {
                    i3 = 20;
                    for (var d2 = !!(16 & e4[s2 + 1]), _2 = !!(8 & e4[s2 + 1]), l2 = s2 + 2, u2 = 0; 255 === e4[l2]; ) u2 += 255, l2 += 1;
                    u2 += e4[l2], l2 += 1, l2 += d2 ? 2 : 0, l2 += _2 ? 2 : 0, n2 = o2;
                    var c2 = Math.floor(o2), h2 = e4.slice(l2, l2 + u2), f2 = { unit: h2, length: h2.byteLength, pts: c2, dts: c2 };
                    this.audio_track_.samples.push(f2), this.audio_track_.length += h2.byteLength, o2 += i3, s2 = l2 + u2;
                  }
                  n2 && (this.audio_last_sample_pts_ = n2);
                }
              }, t3.prototype.parseMP3Payload = function(e4, t4) {
                if (!this.has_video_ || this.video_init_segment_dispatched_) {
                  var i3 = e4[1] >>> 3 & 3, a2 = (6 & e4[1]) >> 1, n2 = (e4[2], (12 & e4[2]) >>> 2), r2 = 3 & ~(e4[3] >>> 6) ? 2 : 1, o2 = 0, s2 = 34;
                  switch (i3) {
                    case 0:
                      o2 = [11025, 12e3, 8e3, 0][n2];
                      break;
                    case 2:
                      o2 = [22050, 24e3, 16e3, 0][n2];
                      break;
                    case 3:
                      o2 = [44100, 48e3, 32e3, 0][n2];
                  }
                  switch (a2) {
                    case 1:
                      s2 = 34;
                      break;
                    case 2:
                      s2 = 33;
                      break;
                    case 3:
                      s2 = 32;
                  }
                  var d2 = new ge();
                  d2.object_type = s2, d2.sample_rate = o2, d2.channel_count = r2, d2.data = e4;
                  var _2 = { codec: "mp3", data: d2 };
                  0 == this.audio_init_segment_dispatched_ ? (this.audio_metadata_ = { codec: "mp3", object_type: s2, sample_rate: o2, channel_count: r2 }, this.dispatchAudioInitSegment(_2)) : this.detectAudioMetadataChange(_2) && (this.dispatchAudioMediaSegment(), this.dispatchAudioInitSegment(_2));
                  var l2 = { unit: e4, length: e4.byteLength, pts: t4 / this.timescale_, dts: t4 / this.timescale_ };
                  this.audio_track_.samples.push(l2), this.audio_track_.length += e4.byteLength;
                }
              }, t3.prototype.detectAudioMetadataChange = function(e4) {
                if (e4.codec !== this.audio_metadata_.codec) return r.default.v(this.TAG, "Audio: Audio Codecs changed from " + "".concat(this.audio_metadata_.codec, " to ").concat(e4.codec)), true;
                if ("aac" === e4.codec && "aac" === this.audio_metadata_.codec) {
                  if ((t4 = e4.data).audio_object_type !== this.audio_metadata_.audio_object_type) return r.default.v(this.TAG, "AAC: AudioObjectType changed from " + "".concat(this.audio_metadata_.audio_object_type, " to ").concat(t4.audio_object_type)), true;
                  if (t4.sampling_freq_index !== this.audio_metadata_.sampling_freq_index) return r.default.v(this.TAG, "AAC: SamplingFrequencyIndex changed from " + "".concat(this.audio_metadata_.sampling_freq_index, " to ").concat(t4.sampling_freq_index)), true;
                  if (t4.channel_config !== this.audio_metadata_.channel_config) return r.default.v(this.TAG, "AAC: Channel configuration changed from " + "".concat(this.audio_metadata_.channel_config, " to ").concat(t4.channel_config)), true;
                } else if ("ac-3" === e4.codec && "ac-3" === this.audio_metadata_.codec) {
                  var t4;
                  if ((t4 = e4.data).sampling_frequency !== this.audio_metadata_.sampling_frequency) return r.default.v(this.TAG, "AC3: Sampling Frequency changed from " + "".concat(this.audio_metadata_.sampling_frequency, " to ").concat(t4.sampling_frequency)), true;
                  if (t4.bit_stream_identification !== this.audio_metadata_.bit_stream_identification) return r.default.v(this.TAG, "AC3: Bit Stream Identification changed from " + "".concat(this.audio_metadata_.bit_stream_identification, " to ").concat(t4.bit_stream_identification)), true;
                  if (t4.bit_stream_mode !== this.audio_metadata_.bit_stream_mode) return r.default.v(this.TAG, "AC3: BitStream Mode changed from " + "".concat(this.audio_metadata_.bit_stream_mode, " to ").concat(t4.bit_stream_mode)), true;
                  if (t4.channel_mode !== this.audio_metadata_.channel_mode) return r.default.v(this.TAG, "AC3: Channel Mode changed from " + "".concat(this.audio_metadata_.channel_mode, " to ").concat(t4.channel_mode)), true;
                  if (t4.low_frequency_effects_channel_on !== this.audio_metadata_.low_frequency_effects_channel_on) return r.default.v(this.TAG, "AC3: Low Frequency Effects Channel On changed from " + "".concat(this.audio_metadata_.low_frequency_effects_channel_on, " to ").concat(t4.low_frequency_effects_channel_on)), true;
                } else if ("opus" === e4.codec && "opus" === this.audio_metadata_.codec) {
                  if ((i3 = e4.meta).sample_rate !== this.audio_metadata_.sample_rate) return r.default.v(this.TAG, "Opus: SamplingFrequencyIndex changed from " + "".concat(this.audio_metadata_.sample_rate, " to ").concat(i3.sample_rate)), true;
                  if (i3.channel_count !== this.audio_metadata_.channel_count) return r.default.v(this.TAG, "Opus: Channel count changed from " + "".concat(this.audio_metadata_.channel_count, " to ").concat(i3.channel_count)), true;
                } else if ("mp3" === e4.codec && "mp3" === this.audio_metadata_.codec) {
                  var i3;
                  if ((i3 = e4.data).object_type !== this.audio_metadata_.object_type) return r.default.v(this.TAG, "MP3: AudioObjectType changed from " + "".concat(this.audio_metadata_.object_type, " to ").concat(i3.object_type)), true;
                  if (i3.sample_rate !== this.audio_metadata_.sample_rate) return r.default.v(this.TAG, "MP3: SamplingFrequencyIndex changed from " + "".concat(this.audio_metadata_.sample_rate, " to ").concat(i3.sample_rate)), true;
                  if (i3.channel_count !== this.audio_metadata_.channel_count) return r.default.v(this.TAG, "MP3: Channel count changed from " + "".concat(this.audio_metadata_.channel_count, " to ").concat(i3.channel_count)), true;
                }
                return false;
              }, t3.prototype.dispatchAudioInitSegment = function(e4) {
                var t4 = { type: "audio" };
                if (t4.id = this.audio_track_.id, t4.timescale = 1e3, t4.duration = this.duration_, "aac" === this.audio_metadata_.codec) {
                  var i3 = "aac" === e4.codec ? e4.data : null, a2 = new Q(i3);
                  t4.audioSampleRate = a2.sampling_rate, t4.channelCount = a2.channel_count, t4.codec = a2.codec_mimetype, t4.originalCodec = a2.original_codec_mimetype, t4.config = a2.config, t4.refSampleDuration = 1024 / t4.audioSampleRate * t4.timescale;
                } else if ("ac-3" === this.audio_metadata_.codec) {
                  var n2 = "ac-3" === e4.codec ? e4.data : null, o2 = new Ee(n2);
                  t4.audioSampleRate = o2.sampling_rate, t4.channelCount = o2.channel_count, t4.codec = o2.codec_mimetype, t4.originalCodec = o2.original_codec_mimetype, t4.config = o2.config, t4.refSampleDuration = 1536 / t4.audioSampleRate * t4.timescale;
                } else if ("ec-3" === this.audio_metadata_.codec) {
                  var s2 = "ec-3" === e4.codec ? e4.data : null, d2 = new Re(s2);
                  t4.audioSampleRate = d2.sampling_rate, t4.channelCount = d2.channel_count, t4.codec = d2.codec_mimetype, t4.originalCodec = d2.original_codec_mimetype, t4.config = d2.config, t4.refSampleDuration = 256 * d2.num_blks / t4.audioSampleRate * t4.timescale;
                } else "opus" === this.audio_metadata_.codec ? (t4.audioSampleRate = this.audio_metadata_.sample_rate, t4.channelCount = this.audio_metadata_.channel_count, t4.channelConfigCode = this.audio_metadata_.channel_config_code, t4.codec = "opus", t4.originalCodec = "opus", t4.config = void 0, t4.refSampleDuration = 20) : "mp3" === this.audio_metadata_.codec && (t4.audioSampleRate = this.audio_metadata_.sample_rate, t4.channelCount = this.audio_metadata_.channel_count, t4.codec = "mp3", t4.originalCodec = "mp3", t4.config = void 0);
                0 == this.audio_init_segment_dispatched_ && r.default.v(this.TAG, "Generated first AudioSpecificConfig for mimeType: ".concat(t4.codec)), this.onTrackMetadata("audio", t4), this.audio_init_segment_dispatched_ = true, this.video_metadata_changed_ = false;
                var _2 = this.media_info_;
                _2.hasAudio = true, _2.audioCodec = t4.originalCodec, _2.audioSampleRate = t4.audioSampleRate, _2.audioChannelCount = t4.channelCount, _2.hasVideo && _2.videoCodec ? _2.mimeType = 'video/mp2t; codecs="'.concat(_2.videoCodec, ",").concat(_2.audioCodec, '"') : _2.mimeType = 'video/mp2t; codecs="'.concat(_2.audioCodec, '"'), _2.isComplete() && this.onMediaInfo(_2);
              }, t3.prototype.dispatchPESPrivateDataDescriptor = function(e4, t4, i3) {
                var a2 = new Z();
                a2.pid = e4, a2.stream_type = t4, a2.descriptor = i3, this.onPESPrivateDataDescriptor && this.onPESPrivateDataDescriptor(a2);
              }, t3.prototype.parsePESPrivateDataPayload = function(e4, t4, i3, a2, n2) {
                var r2 = new J();
                if (r2.pid = a2, r2.stream_id = n2, r2.len = e4.byteLength, r2.data = e4, null != t4) {
                  var o2 = Math.floor(t4 / this.timescale_);
                  r2.pts = o2;
                } else r2.nearest_pts = this.getNearestTimestampMilliseconds();
                if (null != i3) {
                  var s2 = Math.floor(i3 / this.timescale_);
                  r2.dts = s2;
                }
                this.onPESPrivateData && this.onPESPrivateData(r2);
              }, t3.prototype.parseTimedID3MetadataPayload = function(e4, t4, i3, a2, n2) {
                var r2 = new J();
                if (r2.pid = a2, r2.stream_id = n2, r2.len = e4.byteLength, r2.data = e4, null != t4) {
                  var o2 = Math.floor(t4 / this.timescale_);
                  r2.pts = o2;
                }
                if (null != i3) {
                  var s2 = Math.floor(i3 / this.timescale_);
                  r2.dts = s2;
                }
                this.onTimedID3Metadata && this.onTimedID3Metadata(r2);
              }, t3.prototype.parsePGSPayload = function(e4, t4, i3, a2, n2, r2) {
                var o2 = new ke();
                if (o2.pid = a2, o2.lang = r2, o2.stream_id = n2, o2.len = e4.byteLength, o2.data = e4, null != t4) {
                  var s2 = Math.floor(t4 / this.timescale_);
                  o2.pts = s2;
                }
                if (null != i3) {
                  var d2 = Math.floor(i3 / this.timescale_);
                  o2.dts = d2;
                }
                this.onPGSSubtitleData && this.onPGSSubtitleData(o2);
              }, t3.prototype.parseSynchronousKLVMetadataPayload = function(e4, t4, i3, a2, n2) {
                var r2 = new Te();
                if (r2.pid = a2, r2.stream_id = n2, r2.len = e4.byteLength, r2.data = e4, null != t4) {
                  var o2 = Math.floor(t4 / this.timescale_);
                  r2.pts = o2;
                }
                if (null != i3) {
                  var s2 = Math.floor(i3 / this.timescale_);
                  r2.dts = s2;
                }
                r2.access_units = (function(e5) {
                  for (var t5 = [], i4 = 0; i4 + 5 < e5.byteLength; ) {
                    var a3 = e5[i4 + 0], n3 = e5[i4 + 1], r3 = e5[i4 + 2], o3 = e5[i4 + 3] << 8 | e5[i4 + 4], s3 = e5.slice(i4 + 5, i4 + 5 + o3);
                    t5.push({ service_id: a3, sequence_number: n3, flags: r3, data: s3 }), i4 += 5 + o3;
                  }
                  return t5;
                })(e4), this.onSynchronousKLVMetadata && this.onSynchronousKLVMetadata(r2);
              }, t3.prototype.parseAsynchronousKLVMetadataPayload = function(e4, t4, i3) {
                var a2 = new J();
                a2.pid = t4, a2.stream_id = i3, a2.len = e4.byteLength, a2.data = e4, this.onAsynchronousKLVMetadata && this.onAsynchronousKLVMetadata(a2);
              }, t3.prototype.parseSMPTE2038MetadataPayload = function(e4, t4, i3, a2, n2) {
                var r2 = new me();
                if (r2.pid = a2, r2.stream_id = n2, r2.len = e4.byteLength, r2.data = e4, null != t4) {
                  var o2 = Math.floor(t4 / this.timescale_);
                  r2.pts = o2;
                }
                if (r2.nearest_pts = this.getNearestTimestampMilliseconds(), null != i3) {
                  var s2 = Math.floor(i3 / this.timescale_);
                  r2.dts = s2;
                }
                r2.ancillaries = (function(e5) {
                  for (var t5 = new f(e5), i4 = 0, a3 = []; i4 += 6, 0 === t5.readBits(6); ) {
                    var n3 = t5.readBool();
                    i4 += 1;
                    var r3 = t5.readBits(11);
                    i4 += 11;
                    var o3 = t5.readBits(12);
                    i4 += 12;
                    var s3 = 255 & t5.readBits(10);
                    i4 += 10;
                    var d2 = 255 & t5.readBits(10);
                    i4 += 10;
                    var _2 = 255 & t5.readBits(10);
                    i4 += 10;
                    for (var l2 = new Uint8Array(_2), u2 = 0; u2 < _2; u2++) {
                      var c2 = 255 & t5.readBits(10);
                      i4 += 10, l2[u2] = c2;
                    }
                    t5.readBits(10), i4 += 10;
                    var h2 = "User Defined";
                    65 === s3 ? 7 === d2 && (h2 = "SCTE-104") : 95 === s3 ? 220 === d2 ? h2 = "ARIB STD-B37 (1SEG)" : 221 === d2 ? h2 = "ARIB STD-B37 (ANALOG)" : 222 === d2 ? h2 = "ARIB STD-B37 (SD)" : 223 === d2 && (h2 = "ARIB STD-B37 (HD)") : 97 === s3 && (1 === d2 ? h2 = "EIA-708" : 2 === d2 && (h2 = "EIA-608")), a3.push({ yc_indicator: n3, line_number: r3, horizontal_offset: o3, did: s3, sdid: d2, user_data: l2, description: h2, information: {} }), t5.readBits(8 - (i4 - Math.floor(i4 / 8)) % 8), i4 += (8 - (i4 - Math.floor(i4 / 8))) % 8;
                  }
                  return t5.destroy(), t5 = null, a3;
                })(e4), this.onSMPTE2038Metadata && this.onSMPTE2038Metadata(r2);
              }, t3.prototype.parseSEIPayload = function(e4, t4, i3) {
                var a2 = T(e4, null != t4 ? Math.floor(t4 / this.timescale_) : void 0, i3);
                a2 && this.onSEI && this.onSEI(a2);
              }, t3.prototype.getNearestTimestampMilliseconds = function() {
                return null != this.audio_last_sample_pts_ ? Math.floor(this.audio_last_sample_pts_) : null != this.last_pcr_ ? Math.floor(this.last_pcr_ / 300 / this.timescale_) : void 0;
              }, t3.prototype.getPcrBase = function(e4) {
                var t4 = 33554432 * e4[6] + 131072 * e4[7] + 512 * e4[8] + 2 * e4[9] + (128 & e4[10]) / 128 + this.timestamp_offset_;
                return t4 + 4294967296 < this.last_pcr_base_ && (t4 += 8589934592, this.timestamp_offset_ += 8589934592), this.last_pcr_base_ = t4, t4;
              }, t3.prototype.getTimestamp = function(e4, t4) {
                var i3 = 536870912 * (14 & e4[t4]) + 4194304 * (255 & e4[t4 + 1]) + 16384 * (254 & e4[t4 + 2]) + 128 * (255 & e4[t4 + 3]) + (254 & e4[t4 + 4]) / 2 + this.timestamp_offset_;
                return i3 + 4294967296 < this.last_pcr_base_ && (i3 += 8589934592), i3;
              }, t3;
            })(M), we = Ce, Ie = function(e3, t3, i3) {
              if (i3 || 2 === arguments.length) for (var a2, n2 = 0, r2 = t3.length; n2 < r2; n2++) !a2 && n2 in t3 || (a2 || (a2 = Array.prototype.slice.call(t3, 0, n2)), a2[n2] = t3[n2]);
              return e3.concat(a2 || Array.prototype.slice.call(t3));
            }, Be = (function() {
              function e3() {
              }
              return e3.init = function() {
                for (var t3 in e3.types = { avc1: [], avcC: [], btrt: [], dinf: [], dref: [], esds: [], ftyp: [], hdlr: [], hvc1: [], hvcC: [], av01: [], av1C: [], mdat: [], mdhd: [], mdia: [], mfhd: [], minf: [], moof: [], moov: [], mp4a: [], mvex: [], mvhd: [], sdtp: [], stbl: [], stco: [], stsc: [], stsd: [], stsz: [], stts: [], tfdt: [], tfhd: [], traf: [], trak: [], trun: [], trex: [], tkhd: [], vmhd: [], smhd: [], chnl: [], ".mp3": [], Opus: [], dOps: [], fLaC: [], dfLa: [], ipcm: [], pcmC: [], "ac-3": [], dac3: [], "ec-3": [], dec3: [] }, e3.types) e3.types.hasOwnProperty(t3) && (e3.types[t3] = [t3.charCodeAt(0), t3.charCodeAt(1), t3.charCodeAt(2), t3.charCodeAt(3)]);
                var i3 = e3.constants = {};
                i3.FTYP = new Uint8Array([105, 115, 111, 109, 0, 0, 0, 1, 105, 115, 111, 109, 97, 118, 99, 49]), i3.STSD_PREFIX = new Uint8Array([0, 0, 0, 0, 0, 0, 0, 1]), i3.STTS = new Uint8Array([0, 0, 0, 0, 0, 0, 0, 0]), i3.STSC = i3.STCO = i3.STTS, i3.STSZ = new Uint8Array([0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]), i3.HDLR_VIDEO = new Uint8Array([0, 0, 0, 0, 0, 0, 0, 0, 118, 105, 100, 101, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 86, 105, 100, 101, 111, 72, 97, 110, 100, 108, 101, 114, 0]), i3.HDLR_AUDIO = new Uint8Array([0, 0, 0, 0, 0, 0, 0, 0, 115, 111, 117, 110, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 83, 111, 117, 110, 100, 72, 97, 110, 100, 108, 101, 114, 0]), i3.DREF = new Uint8Array([0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 12, 117, 114, 108, 32, 0, 0, 0, 1]), i3.SMHD = new Uint8Array([0, 0, 0, 0, 0, 0, 0, 0]), i3.VMHD = new Uint8Array([0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0]);
              }, e3.box = function(e4) {
                for (var t3 = 8, i3 = null, a2 = Array.prototype.slice.call(arguments, 1), n2 = a2.length, r2 = 0; r2 < n2; r2++) t3 += a2[r2].byteLength;
                (i3 = new Uint8Array(t3))[0] = t3 >>> 24 & 255, i3[1] = t3 >>> 16 & 255, i3[2] = t3 >>> 8 & 255, i3[3] = 255 & t3, i3.set(e4, 4);
                var o2 = 8;
                for (r2 = 0; r2 < n2; r2++) i3.set(a2[r2], o2), o2 += a2[r2].byteLength;
                return i3;
              }, e3.generateInitSegment = function(t3) {
                var i3 = e3.box(e3.types.ftyp, e3.constants.FTYP), a2 = e3.moov(t3), n2 = new Uint8Array(i3.byteLength + a2.byteLength);
                return n2.set(i3, 0), n2.set(a2, i3.byteLength), n2;
              }, e3.moov = function(t3) {
                var i3 = e3.mvhd(t3.timescale, t3.duration), a2 = e3.trak(t3), n2 = e3.mvex(t3);
                return e3.box(e3.types.moov, i3, a2, n2);
              }, e3.mvhd = function(t3, i3) {
                return e3.box(e3.types.mvhd, new Uint8Array([0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, t3 >>> 24 & 255, t3 >>> 16 & 255, t3 >>> 8 & 255, 255 & t3, i3 >>> 24 & 255, i3 >>> 16 & 255, i3 >>> 8 & 255, 255 & i3, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 255]));
              }, e3.trak = function(t3) {
                return e3.box(e3.types.trak, e3.tkhd(t3), e3.mdia(t3));
              }, e3.tkhd = function(t3) {
                var i3 = t3.id, a2 = t3.duration, n2 = t3.presentWidth, r2 = t3.presentHeight;
                return e3.box(e3.types.tkhd, new Uint8Array([0, 0, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0, i3 >>> 24 & 255, i3 >>> 16 & 255, i3 >>> 8 & 255, 255 & i3, 0, 0, 0, 0, a2 >>> 24 & 255, a2 >>> 16 & 255, a2 >>> 8 & 255, 255 & a2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 64, 0, 0, 0, n2 >>> 8 & 255, 255 & n2, 0, 0, r2 >>> 8 & 255, 255 & r2, 0, 0]));
              }, e3.mdia = function(t3) {
                return e3.box(e3.types.mdia, e3.mdhd(t3), e3.hdlr(t3), e3.minf(t3));
              }, e3.mdhd = function(t3) {
                var i3 = t3.timescale, a2 = t3.duration;
                return e3.box(e3.types.mdhd, new Uint8Array([0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, i3 >>> 24 & 255, i3 >>> 16 & 255, i3 >>> 8 & 255, 255 & i3, a2 >>> 24 & 255, a2 >>> 16 & 255, a2 >>> 8 & 255, 255 & a2, 85, 196, 0, 0]));
              }, e3.hdlr = function(t3) {
                var i3;
                return i3 = "audio" === t3.type ? e3.constants.HDLR_AUDIO : e3.constants.HDLR_VIDEO, e3.box(e3.types.hdlr, i3);
              }, e3.minf = function(t3) {
                var i3;
                return i3 = "audio" === t3.type ? e3.box(e3.types.smhd, e3.constants.SMHD) : e3.box(e3.types.vmhd, e3.constants.VMHD), e3.box(e3.types.minf, i3, e3.dinf(), e3.stbl(t3));
              }, e3.dinf = function() {
                return e3.box(e3.types.dinf, e3.box(e3.types.dref, e3.constants.DREF));
              }, e3.stbl = function(t3) {
                return e3.box(e3.types.stbl, e3.stsd(t3), e3.box(e3.types.stts, e3.constants.STTS), e3.box(e3.types.stsc, e3.constants.STSC), e3.box(e3.types.stsz, e3.constants.STSZ), e3.box(e3.types.stco, e3.constants.STCO));
              }, e3.stsd = function(t3) {
                return "audio" === t3.type ? "mp3" === t3.codec ? e3.box(e3.types.stsd, e3.constants.STSD_PREFIX, e3.mp3(t3)) : "ac-3" === t3.codec ? e3.box(e3.types.stsd, e3.constants.STSD_PREFIX, e3.ac3(t3)) : "ec-3" === t3.codec ? e3.box(e3.types.stsd, e3.constants.STSD_PREFIX, e3.ec3(t3)) : "opus" === t3.codec ? e3.box(e3.types.stsd, e3.constants.STSD_PREFIX, e3.Opus(t3)) : "flac" == t3.codec ? e3.box(e3.types.stsd, e3.constants.STSD_PREFIX, e3.fLaC(t3)) : "ipcm" == t3.codec ? e3.box(e3.types.stsd, e3.constants.STSD_PREFIX, e3.ipcm(t3)) : e3.box(e3.types.stsd, e3.constants.STSD_PREFIX, e3.mp4a(t3)) : "video" === t3.type && t3.codec.startsWith("hvc1") ? e3.box(e3.types.stsd, e3.constants.STSD_PREFIX, e3.hvc1(t3)) : "video" === t3.type && t3.codec.startsWith("av01") ? e3.box(e3.types.stsd, e3.constants.STSD_PREFIX, e3.av01(t3)) : e3.box(e3.types.stsd, e3.constants.STSD_PREFIX, e3.avc1(t3));
              }, e3.mp3 = function(t3) {
                var i3 = t3.channelCount, a2 = t3.audioSampleRate, n2 = new Uint8Array([0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, i3, 0, 16, 0, 0, 0, 0, a2 >>> 8 & 255, 255 & a2, 0, 0]);
                return e3.box(e3.types[".mp3"], n2);
              }, e3.mp4a = function(t3) {
                var i3 = t3.channelCount, a2 = t3.audioSampleRate, n2 = new Uint8Array([0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, i3, 0, 16, 0, 0, 0, 0, a2 >>> 8 & 255, 255 & a2, 0, 0]);
                return e3.box(e3.types.mp4a, n2, e3.esds(t3));
              }, e3.ac3 = function(t3) {
                var i3 = t3.channelCount, a2 = t3.audioSampleRate, n2 = new Uint8Array([0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, i3, 0, 16, 0, 0, 0, 0, a2 >>> 8 & 255, 255 & a2, 0, 0]);
                return e3.box(e3.types["ac-3"], n2, e3.box(e3.types.dac3, new Uint8Array(t3.config)));
              }, e3.ec3 = function(t3) {
                var i3 = t3.channelCount, a2 = t3.audioSampleRate, n2 = new Uint8Array([0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, i3, 0, 16, 0, 0, 0, 0, a2 >>> 8 & 255, 255 & a2, 0, 0]);
                return e3.box(e3.types["ec-3"], n2, e3.box(e3.types.dec3, new Uint8Array(t3.config)));
              }, e3.esds = function(t3) {
                var i3 = t3.config || [], a2 = i3.length, n2 = new Uint8Array([0, 0, 0, 0, 3, 23 + a2, 0, 1, 0, 4, 15 + a2, 64, 21, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5].concat([a2]).concat(i3).concat([6, 1, 2]));
                return e3.box(e3.types.esds, n2);
              }, e3.Opus = function(t3) {
                var i3 = t3.channelCount, a2 = t3.audioSampleRate, n2 = new Uint8Array([0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, i3, 0, 16, 0, 0, 0, 0, a2 >>> 8 & 255, 255 & a2, 0, 0]);
                return e3.box(e3.types.Opus, n2, e3.dOps(t3));
              }, e3.dOps = function(t3) {
                var i3 = t3.channelCount, a2 = t3.channelConfigCode, n2 = t3.audioSampleRate;
                if (t3.config) return e3.box(e3.types.dOps, t3.config);
                var r2 = [];
                switch (a2) {
                  case 1:
                  case 2:
                    r2 = [0];
                    break;
                  case 0:
                    r2 = [255, 1, 1, 0, 1];
                    break;
                  case 128:
                    r2 = [255, 2, 0, 0, 1];
                    break;
                  case 3:
                    r2 = [1, 2, 1, 0, 2, 1];
                    break;
                  case 4:
                    r2 = [1, 2, 2, 0, 1, 2, 3];
                    break;
                  case 5:
                    r2 = [1, 3, 2, 0, 4, 1, 2, 3];
                    break;
                  case 6:
                    r2 = [1, 4, 2, 0, 4, 1, 2, 3, 5];
                    break;
                  case 7:
                    r2 = [1, 4, 2, 0, 4, 1, 2, 3, 5, 6];
                    break;
                  case 8:
                    r2 = [1, 5, 3, 0, 6, 1, 2, 3, 4, 5, 7];
                    break;
                  case 130:
                    r2 = [1, 1, 2, 0, 1];
                    break;
                  case 131:
                    r2 = [1, 1, 3, 0, 1, 2];
                    break;
                  case 132:
                    r2 = [1, 1, 4, 0, 1, 2, 3];
                    break;
                  case 133:
                    r2 = [1, 1, 5, 0, 1, 2, 3, 4];
                    break;
                  case 134:
                    r2 = [1, 1, 6, 0, 1, 2, 3, 4, 5];
                    break;
                  case 135:
                    r2 = [1, 1, 7, 0, 1, 2, 3, 4, 5, 6];
                    break;
                  case 136:
                    r2 = [1, 1, 8, 0, 1, 2, 3, 4, 5, 6, 7];
                }
                var o2 = new Uint8Array(Ie([0, i3, 0, 0, n2 >>> 24 & 255, n2 >>> 17 & 255, n2 >>> 8 & 255, n2 >>> 0 & 255, 0, 0], r2, true));
                return e3.box(e3.types.dOps, o2);
              }, e3.fLaC = function(t3) {
                var i3 = t3.channelCount, a2 = Math.min(t3.audioSampleRate, 65535), n2 = t3.sampleSize, r2 = new Uint8Array([0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, i3, 0, n2, 0, 0, 0, 0, a2 >>> 8 & 255, 255 & a2, 0, 0]);
                return e3.box(e3.types.fLaC, r2, e3.dfLa(t3));
              }, e3.dfLa = function(t3) {
                var i3 = new Uint8Array(Ie([0, 0, 0, 0], t3.config, true));
                return e3.box(e3.types.dfLa, i3);
              }, e3.ipcm = function(t3) {
                var i3 = t3.channelCount, a2 = Math.min(t3.audioSampleRate, 65535), n2 = t3.sampleSize, r2 = new Uint8Array([0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, i3, 0, n2, 0, 0, 0, 0, a2 >>> 8 & 255, 255 & a2, 0, 0]);
                return 1 === t3.channelCount ? e3.box(e3.types.ipcm, r2, e3.pcmC(t3)) : e3.box(e3.types.ipcm, r2, e3.chnl(t3), e3.pcmC(t3));
              }, e3.chnl = function(t3) {
                var i3 = new Uint8Array([0, 0, 0, 0, 1, t3.channelCount, 0, 0, 0, 0, 0, 0, 0, 0]);
                return e3.box(e3.types.chnl, i3);
              }, e3.pcmC = function(t3) {
                var i3 = t3.littleEndian ? 1 : 0, a2 = t3.sampleSize, n2 = new Uint8Array([0, 0, 0, 0, i3, a2]);
                return e3.box(e3.types.pcmC, n2);
              }, e3.avc1 = function(t3) {
                var i3 = t3.avcc, a2 = t3.codecWidth, n2 = t3.codecHeight, r2 = new Uint8Array([0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, a2 >>> 8 & 255, 255 & a2, n2 >>> 8 & 255, 255 & n2, 0, 72, 0, 0, 0, 72, 0, 0, 0, 0, 0, 0, 0, 1, 10, 120, 113, 113, 47, 102, 108, 118, 46, 106, 115, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 24, 255, 255]);
                return e3.box(e3.types.avc1, r2, e3.box(e3.types.avcC, i3));
              }, e3.hvc1 = function(t3) {
                var i3 = t3.hvcc, a2 = t3.codecWidth, n2 = t3.codecHeight, r2 = new Uint8Array([0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, a2 >>> 8 & 255, 255 & a2, n2 >>> 8 & 255, 255 & n2, 0, 72, 0, 0, 0, 72, 0, 0, 0, 0, 0, 0, 0, 1, 10, 120, 113, 113, 47, 102, 108, 118, 46, 106, 115, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 24, 255, 255]);
                return e3.box(e3.types.hvc1, r2, e3.box(e3.types.hvcC, i3));
              }, e3.av01 = function(t3) {
                var i3 = t3.av1c, a2 = t3.codecWidth || 192, n2 = t3.codecHeight || 108, r2 = new Uint8Array([0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, a2 >>> 8 & 255, 255 & a2, n2 >>> 8 & 255, 255 & n2, 0, 72, 0, 0, 0, 72, 0, 0, 0, 0, 0, 0, 0, 1, 10, 120, 113, 113, 47, 102, 108, 118, 46, 106, 115, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 24, 255, 255]);
                return e3.box(e3.types.av01, r2, e3.box(e3.types.av1C, i3));
              }, e3.mvex = function(t3) {
                return e3.box(e3.types.mvex, e3.trex(t3));
              }, e3.trex = function(t3) {
                var i3 = t3.id, a2 = new Uint8Array([0, 0, 0, 0, i3 >>> 24 & 255, i3 >>> 16 & 255, i3 >>> 8 & 255, 255 & i3, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1]);
                return e3.box(e3.types.trex, a2);
              }, e3.moof = function(t3, i3) {
                return e3.box(e3.types.moof, e3.mfhd(t3.sequenceNumber), e3.traf(t3, i3));
              }, e3.mfhd = function(t3) {
                var i3 = new Uint8Array([0, 0, 0, 0, t3 >>> 24 & 255, t3 >>> 16 & 255, t3 >>> 8 & 255, 255 & t3]);
                return e3.box(e3.types.mfhd, i3);
              }, e3.traf = function(t3, i3) {
                var a2 = t3.id, n2 = e3.box(e3.types.tfhd, new Uint8Array([0, 0, 0, 0, a2 >>> 24 & 255, a2 >>> 16 & 255, a2 >>> 8 & 255, 255 & a2])), r2 = e3.box(e3.types.tfdt, new Uint8Array([0, 0, 0, 0, i3 >>> 24 & 255, i3 >>> 16 & 255, i3 >>> 8 & 255, 255 & i3])), o2 = e3.sdtp(t3), s2 = e3.trun(t3, o2.byteLength + 16 + 16 + 8 + 16 + 8 + 8);
                return e3.box(e3.types.traf, n2, r2, s2, o2);
              }, e3.sdtp = function(t3) {
                for (var i3 = t3.samples || [], a2 = i3.length, n2 = new Uint8Array(4 + a2), r2 = 0; r2 < a2; r2++) {
                  var o2 = i3[r2].flags;
                  n2[r2 + 4] = o2.isLeading << 6 | o2.dependsOn << 4 | o2.isDependedOn << 2 | o2.hasRedundancy;
                }
                return e3.box(e3.types.sdtp, n2);
              }, e3.trun = function(t3, i3) {
                var a2 = t3.samples || [], n2 = a2.length, r2 = 12 + 16 * n2, o2 = new Uint8Array(r2);
                i3 += 8 + r2, o2.set([0, 0, 15, 1, n2 >>> 24 & 255, n2 >>> 16 & 255, n2 >>> 8 & 255, 255 & n2, i3 >>> 24 & 255, i3 >>> 16 & 255, i3 >>> 8 & 255, 255 & i3], 0);
                for (var s2 = 0; s2 < n2; s2++) {
                  var d2 = a2[s2].duration, _2 = a2[s2].size, l2 = a2[s2].flags, u2 = a2[s2].cts;
                  o2.set([d2 >>> 24 & 255, d2 >>> 16 & 255, d2 >>> 8 & 255, 255 & d2, _2 >>> 24 & 255, _2 >>> 16 & 255, _2 >>> 8 & 255, 255 & _2, l2.isLeading << 2 | l2.dependsOn, l2.isDependedOn << 6 | l2.hasRedundancy << 4 | l2.isNonSync, 0, 0, u2 >>> 24 & 255, u2 >>> 16 & 255, u2 >>> 8 & 255, 255 & u2], 12 + 16 * s2);
                }
                return e3.box(e3.types.trun, o2);
              }, e3.mdat = function(t3) {
                return e3.box(e3.types.mdat, t3);
              }, e3;
            })();
            Be.init();
            var Oe = Be, Pe = (function() {
              function e3() {
              }
              return e3.getSilentFrame = function(e4, t3) {
                if ("mp4a.40.2" === e4) {
                  if (1 === t3) return new Uint8Array([0, 200, 0, 128, 35, 128]);
                  if (2 === t3) return new Uint8Array([33, 0, 73, 144, 2, 25, 0, 35, 128]);
                  if (3 === t3) return new Uint8Array([0, 200, 0, 128, 32, 132, 1, 38, 64, 8, 100, 0, 142]);
                  if (4 === t3) return new Uint8Array([0, 200, 0, 128, 32, 132, 1, 38, 64, 8, 100, 0, 128, 44, 128, 8, 2, 56]);
                  if (5 === t3) return new Uint8Array([0, 200, 0, 128, 32, 132, 1, 38, 64, 8, 100, 0, 130, 48, 4, 153, 0, 33, 144, 2, 56]);
                  if (6 === t3) return new Uint8Array([0, 200, 0, 128, 32, 132, 1, 38, 64, 8, 100, 0, 130, 48, 4, 153, 0, 33, 144, 2, 0, 178, 0, 32, 8, 224]);
                } else {
                  if (1 === t3) return new Uint8Array([1, 64, 34, 128, 163, 78, 230, 128, 186, 8, 0, 0, 0, 28, 6, 241, 193, 10, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 94]);
                  if (2 === t3) return new Uint8Array([1, 64, 34, 128, 163, 94, 230, 128, 186, 8, 0, 0, 0, 0, 149, 0, 6, 241, 161, 10, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 94]);
                  if (3 === t3) return new Uint8Array([1, 64, 34, 128, 163, 94, 230, 128, 186, 8, 0, 0, 0, 0, 149, 0, 6, 241, 161, 10, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 94]);
                }
                return null;
              }, e3;
            })(), xe = i2(47), Ue = (function() {
              function e3(e4) {
                this.TAG = "MP4Remuxer", this._config = e4, this._isLive = true === e4.isLive, this._dtsBase = -1, this._dtsBaseInited = false, this._audioDtsBase = 1 / 0, this._videoDtsBase = 1 / 0, this._audioNextDts = void 0, this._videoNextDts = void 0, this._audioStashedLastSample = null, this._videoStashedLastSample = null, this._audioMeta = null, this._videoMeta = null, this._audioSegmentInfoList = new xe.MediaSegmentInfoList("audio"), this._videoSegmentInfoList = new xe.MediaSegmentInfoList("video"), this._onInitSegment = null, this._onMediaSegment = null, this._forceFirstIDR = !(!o.default.chrome || !(o.default.version.major < 50 || 50 === o.default.version.major && o.default.version.build < 2661)), this._fillSilentAfterSeek = o.default.msedge || o.default.msie, this._mp3UseMpegAudio = !o.default.firefox, this._fillAudioTimestampGap = this._config.fixAudioTimestampGap;
              }
              return e3.prototype.destroy = function() {
                this._dtsBase = -1, this._dtsBaseInited = false, this._audioMeta = null, this._videoMeta = null, this._audioSegmentInfoList.clear(), this._audioSegmentInfoList = null, this._videoSegmentInfoList.clear(), this._videoSegmentInfoList = null, this._onInitSegment = null, this._onMediaSegment = null;
              }, e3.prototype.bindDataSource = function(e4) {
                return e4.onDataAvailable = this.remux.bind(this), e4.onTrackMetadata = this._onTrackMetadataReceived.bind(this), this;
              }, Object.defineProperty(e3.prototype, "onInitSegment", { get: function() {
                return this._onInitSegment;
              }, set: function(e4) {
                this._onInitSegment = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "onMediaSegment", { get: function() {
                return this._onMediaSegment;
              }, set: function(e4) {
                this._onMediaSegment = e4;
              }, enumerable: false, configurable: true }), e3.prototype.insertDiscontinuity = function() {
                this._audioNextDts = this._videoNextDts = void 0;
              }, e3.prototype.seek = function(e4) {
                this._audioStashedLastSample = null, this._videoStashedLastSample = null, this._videoSegmentInfoList.clear(), this._audioSegmentInfoList.clear();
              }, e3.prototype.remux = function(e4, t3) {
                if (!this._onMediaSegment) throw new u.IllegalStateException("MP4Remuxer: onMediaSegment callback must be specificed!");
                this._dtsBaseInited || this._calculateDtsBase(e4, t3), t3 && this._remuxVideo(t3), e4 && this._remuxAudio(e4);
              }, e3.prototype._onTrackMetadataReceived = function(e4, t3) {
                var i3 = null, a2 = "mp4", n2 = t3.codec;
                if ("audio" === e4) this._audioMeta = t3, "mp3" === t3.codec && this._mp3UseMpegAudio ? (a2 = "mpeg", n2 = "", i3 = new Uint8Array()) : i3 = Oe.generateInitSegment(t3);
                else {
                  if ("video" !== e4) return;
                  this._videoMeta = t3, i3 = Oe.generateInitSegment(t3);
                }
                if (!this._onInitSegment) throw new u.IllegalStateException("MP4Remuxer: onInitSegment callback must be specified!");
                this._onInitSegment(e4, { type: e4, data: i3.buffer, codec: n2, container: "".concat(e4, "/").concat(a2), mediaDuration: t3.duration });
              }, e3.prototype._calculateDtsBase = function(e4, t3) {
                this._dtsBaseInited || (e4 && e4.samples && e4.samples.length && (this._audioDtsBase = e4.samples[0].dts), t3 && t3.samples && t3.samples.length && (this._videoDtsBase = t3.samples[0].dts), this._dtsBase = Math.min(this._audioDtsBase, this._videoDtsBase), this._dtsBaseInited = true);
              }, e3.prototype.getTimestampBase = function() {
                if (this._dtsBaseInited) return this._dtsBase;
              }, e3.prototype.flushStashedSamples = function() {
                var e4 = this._videoStashedLastSample, t3 = this._audioStashedLastSample, i3 = { type: "video", id: 1, sequenceNumber: 0, samples: [], length: 0 };
                null != e4 && (i3.samples.push(e4), i3.length = e4.length);
                var a2 = { type: "audio", id: 2, sequenceNumber: 0, samples: [], length: 0 };
                null != t3 && (a2.samples.push(t3), a2.length = t3.length), this._videoStashedLastSample = null, this._audioStashedLastSample = null, this._remuxVideo(i3, true), this._remuxAudio(a2, true);
              }, e3.prototype._remuxAudio = function(e4, t3) {
                if (null != this._audioMeta) {
                  var i3, a2 = e4, n2 = a2.samples, o2 = void 0, s2 = -1, d2 = this._audioMeta.refSampleDuration, _2 = "mp3" === this._audioMeta.codec && this._mp3UseMpegAudio, l2 = this._dtsBaseInited && void 0 === this._audioNextDts, u2 = false;
                  if (n2 && 0 !== n2.length && (1 !== n2.length || t3)) {
                    var c2 = 0, h2 = null, f2 = 0;
                    _2 ? (c2 = 0, f2 = a2.length) : (c2 = 8, f2 = 8 + a2.length);
                    var p2 = null;
                    if (n2.length > 1 && (f2 -= (p2 = n2.pop()).length), null != this._audioStashedLastSample) {
                      var m2 = this._audioStashedLastSample;
                      this._audioStashedLastSample = null, n2.unshift(m2), f2 += m2.length;
                    }
                    null != p2 && (this._audioStashedLastSample = p2);
                    var g2 = n2[0].dts - this._dtsBase;
                    if (this._audioNextDts) o2 = g2 - this._audioNextDts;
                    else if (this._audioSegmentInfoList.isEmpty()) o2 = 0, this._fillSilentAfterSeek && !this._videoSegmentInfoList.isEmpty() && "mp3" !== this._audioMeta.originalCodec && (u2 = true);
                    else {
                      var v2 = this._audioSegmentInfoList.getLastSampleBefore(g2);
                      if (null != v2) {
                        var y2 = g2 - (v2.originalDts + v2.duration);
                        y2 <= 3 && (y2 = 0), o2 = g2 - (v2.dts + v2.duration + y2);
                      } else o2 = 0;
                    }
                    if (u2) {
                      var S2 = g2 - o2, E2 = this._videoSegmentInfoList.getLastSegmentBefore(g2);
                      if (null != E2 && E2.beginDts < S2) {
                        if (I2 = Pe.getSilentFrame(this._audioMeta.originalCodec, this._audioMeta.channelCount)) {
                          var A2 = E2.beginDts, b2 = S2 - E2.beginDts;
                          r.default.v(this.TAG, "InsertPrefixSilentAudio: dts: ".concat(A2, ", duration: ").concat(b2)), n2.unshift({ unit: I2, dts: A2, pts: A2 }), f2 += I2.byteLength;
                        }
                      } else u2 = false;
                    }
                    for (var R2 = [], T2 = 0; T2 < n2.length; T2++) {
                      var L2 = (m2 = n2[T2]).unit, k2 = m2.dts - this._dtsBase, D2 = (A2 = k2, false), M2 = null, C2 = 0;
                      if (!(k2 < -1e-3)) {
                        if ("mp3" !== this._audioMeta.codec && null != d2) {
                          var w2 = k2;
                          if (this._audioNextDts && (w2 = this._audioNextDts), (o2 = k2 - w2) <= -3 * d2) {
                            r.default.w(this.TAG, "Dropping 1 audio frame (originalDts: ".concat(k2, " ms ,curRefDts: ").concat(w2, " ms)  due to dtsCorrection: ").concat(o2, " ms overlap."));
                            continue;
                          }
                          if (o2 >= 3 * d2 && this._fillAudioTimestampGap) {
                            D2 = true;
                            var I2, B2 = Math.floor(o2 / d2);
                            r.default.w(this.TAG, "Large audio timestamp gap detected, may cause AV sync to drift. Silent frames will be generated to avoid unsync.\n" + "originalDts: ".concat(k2, " ms, curRefDts: ").concat(w2, " ms, ") + "dtsCorrection: ".concat(Math.round(o2), " ms, generate: ").concat(B2, " frames")), A2 = Math.floor(w2), C2 = Math.floor(w2 + d2) - A2, null == (I2 = Pe.getSilentFrame(this._audioMeta.originalCodec, this._audioMeta.channelCount)) && (r.default.w(this.TAG, "Unable to generate silent frame for " + "".concat(this._audioMeta.originalCodec, " with ").concat(this._audioMeta.channelCount, " channels, repeat last frame")), I2 = L2), M2 = [];
                            for (var O2 = 0; O2 < B2; O2++) {
                              w2 += d2;
                              var P2 = Math.floor(w2), x2 = Math.floor(w2 + d2) - P2, U2 = { dts: P2, pts: P2, cts: 0, unit: I2, size: I2.byteLength, duration: x2, originalDts: k2, flags: { isLeading: 0, dependsOn: 1, isDependedOn: 0, hasRedundancy: 0 } };
                              M2.push(U2), f2 += U2.size;
                            }
                            this._audioNextDts = w2 + d2;
                          } else A2 = Math.floor(w2), C2 = Math.floor(w2 + d2) - A2, this._audioNextDts = w2 + d2;
                        } else A2 = k2 - o2, C2 = T2 !== n2.length - 1 ? n2[T2 + 1].dts - this._dtsBase - o2 - A2 : null != p2 ? p2.dts - this._dtsBase - o2 - A2 : R2.length >= 1 ? R2[R2.length - 1].duration : Math.floor(d2), this._audioNextDts = A2 + C2;
                        -1 === s2 && (s2 = A2), R2.push({ dts: A2, pts: A2, cts: 0, unit: m2.unit, size: m2.unit.byteLength, duration: C2, originalDts: k2, flags: { isLeading: 0, dependsOn: 1, isDependedOn: 0, hasRedundancy: 0 } }), D2 && R2.push.apply(R2, M2);
                      }
                    }
                    if (0 === R2.length) return a2.samples = [], void (a2.length = 0);
                    for (_2 ? h2 = new Uint8Array(f2) : ((h2 = new Uint8Array(f2))[0] = f2 >>> 24 & 255, h2[1] = f2 >>> 16 & 255, h2[2] = f2 >>> 8 & 255, h2[3] = 255 & f2, h2.set(Oe.types.mdat, 4)), T2 = 0; T2 < R2.length; T2++) L2 = R2[T2].unit, h2.set(L2, c2), c2 += L2.byteLength;
                    var N2 = R2[R2.length - 1];
                    i3 = N2.dts + N2.duration;
                    var V2 = new xe.MediaSegmentInfo();
                    V2.beginDts = s2, V2.endDts = i3, V2.beginPts = s2, V2.endPts = i3, V2.originalBeginDts = R2[0].originalDts, V2.originalEndDts = N2.originalDts + N2.duration, V2.firstSample = new xe.SampleInfo(R2[0].dts, R2[0].pts, R2[0].duration, R2[0].originalDts, false), V2.lastSample = new xe.SampleInfo(N2.dts, N2.pts, N2.duration, N2.originalDts, false), this._isLive || this._audioSegmentInfoList.append(V2), a2.samples = R2, a2.sequenceNumber++;
                    var G2;
                    G2 = _2 ? new Uint8Array() : Oe.moof(a2, s2), a2.samples = [], a2.length = 0;
                    var F2 = { type: "audio", data: this._mergeBoxes(G2, h2).buffer, sampleCount: R2.length, info: V2 };
                    _2 && l2 && (F2.timestampOffset = s2), this._onMediaSegment("audio", F2);
                  }
                }
              }, e3.prototype._remuxVideo = function(e4, t3) {
                if (null != this._videoMeta) {
                  var i3, a2, n2 = e4, r2 = n2.samples, o2 = void 0, s2 = -1, d2 = -1;
                  if (r2 && 0 !== r2.length && (1 !== r2.length || t3)) {
                    var _2 = 8, l2 = null, u2 = 8 + e4.length, c2 = null;
                    if (r2.length > 1 && (u2 -= (c2 = r2.pop()).length), null != this._videoStashedLastSample) {
                      var h2 = this._videoStashedLastSample;
                      this._videoStashedLastSample = null, r2.unshift(h2), u2 += h2.length;
                    }
                    null != c2 && (this._videoStashedLastSample = c2);
                    var f2 = r2[0].dts - this._dtsBase;
                    if (this._videoNextDts) o2 = f2 - this._videoNextDts;
                    else if (this._videoSegmentInfoList.isEmpty()) o2 = 0;
                    else {
                      var p2 = this._videoSegmentInfoList.getLastSampleBefore(f2);
                      if (null != p2) {
                        var m2 = f2 - (p2.originalDts + p2.duration);
                        m2 <= 3 && (m2 = 0), o2 = f2 - (p2.dts + p2.duration + m2);
                      } else o2 = 0;
                    }
                    for (var g2 = new xe.MediaSegmentInfo(), v2 = [], y2 = 0; y2 < r2.length; y2++) {
                      var S2 = (h2 = r2[y2]).dts - this._dtsBase, E2 = h2.isKeyframe, A2 = S2 - o2, b2 = h2.cts, R2 = A2 + b2;
                      -1 === s2 && (s2 = A2, d2 = R2);
                      var T2 = 0;
                      if (T2 = y2 !== r2.length - 1 ? r2[y2 + 1].dts - this._dtsBase - o2 - A2 : null != c2 ? c2.dts - this._dtsBase - o2 - A2 : v2.length >= 1 ? v2[v2.length - 1].duration : Math.floor(this._videoMeta.refSampleDuration), E2) {
                        var L2 = new xe.SampleInfo(A2, R2, T2, h2.dts, true);
                        L2.fileposition = h2.fileposition, g2.appendSyncPoint(L2);
                      }
                      v2.push({ dts: A2, pts: R2, cts: b2, units: h2.units, size: h2.length, isKeyframe: E2, duration: T2, originalDts: S2, flags: { isLeading: 0, dependsOn: E2 ? 2 : 1, isDependedOn: E2 ? 1 : 0, hasRedundancy: 0, isNonSync: E2 ? 0 : 1 } });
                    }
                    for ((l2 = new Uint8Array(u2))[0] = u2 >>> 24 & 255, l2[1] = u2 >>> 16 & 255, l2[2] = u2 >>> 8 & 255, l2[3] = 255 & u2, l2.set(Oe.types.mdat, 4), y2 = 0; y2 < v2.length; y2++) for (var k2 = v2[y2].units; k2.length; ) {
                      var D2 = k2.shift().data;
                      l2.set(D2, _2), _2 += D2.byteLength;
                    }
                    var M2 = v2[v2.length - 1];
                    if (i3 = M2.dts + M2.duration, a2 = M2.pts + M2.duration, this._videoNextDts = i3, g2.beginDts = s2, g2.endDts = i3, g2.beginPts = d2, g2.endPts = a2, g2.originalBeginDts = v2[0].originalDts, g2.originalEndDts = M2.originalDts + M2.duration, g2.firstSample = new xe.SampleInfo(v2[0].dts, v2[0].pts, v2[0].duration, v2[0].originalDts, v2[0].isKeyframe), g2.lastSample = new xe.SampleInfo(M2.dts, M2.pts, M2.duration, M2.originalDts, M2.isKeyframe), this._isLive || this._videoSegmentInfoList.append(g2), n2.samples = v2, n2.sequenceNumber++, this._forceFirstIDR) {
                      var C2 = v2[0].flags;
                      C2.dependsOn = 2, C2.isNonSync = 0;
                    }
                    var w2 = Oe.moof(n2, s2);
                    n2.samples = [], n2.length = 0, this._onMediaSegment("video", { type: "video", data: this._mergeBoxes(w2, l2).buffer, sampleCount: v2.length, info: g2 });
                  }
                }
              }, e3.prototype._mergeBoxes = function(e4, t3) {
                var i3 = new Uint8Array(e4.byteLength + t3.byteLength);
                return i3.set(e4, 0), i3.set(t3, e4.byteLength), i3;
              }, e3;
            })(), Ne = i2(653), Ve = i2(726), Ge = (i2(470), (function() {
              function e3(e4, t3) {
                this.TAG = "TransmuxingController", this._emitter = new (n())(), this._config = t3, e4.segments || (e4.segments = [{ duration: e4.duration, filesize: e4.filesize, url: e4.url }]), "boolean" != typeof e4.cors && (e4.cors = true), "boolean" != typeof e4.withCredentials && (e4.withCredentials = false), this._mediaDataSource = e4, this._currentSegmentIndex = 0;
                var i3 = 0;
                this._mediaDataSource.segments.forEach(function(a2) {
                  a2.timestampBase = i3, i3 += a2.duration, a2.cors = e4.cors, a2.withCredentials = e4.withCredentials, t3.referrerPolicy && (a2.referrerPolicy = t3.referrerPolicy);
                }), isNaN(i3) || this._mediaDataSource.duration === i3 || (this._mediaDataSource.duration = i3), this._mediaInfo = null, this._demuxer = null, this._remuxer = null, this._ioctl = null, this._pendingSeekTime = null, this._pendingResolveSeekPoint = null, this._statisticsReporter = null;
              }
              return e3.prototype.destroy = function() {
                this._mediaInfo = null, this._mediaDataSource = null, this._statisticsReporter && this._disableStatisticsReporter(), this._ioctl && (this._ioctl.destroy(), this._ioctl = null), this._demuxer && (this._demuxer.destroy(), this._demuxer = null), this._remuxer && (this._remuxer.destroy(), this._remuxer = null), this._emitter.removeAllListeners(), this._emitter = null;
              }, e3.prototype.on = function(e4, t3) {
                this._emitter.addListener(e4, t3);
              }, e3.prototype.off = function(e4, t3) {
                this._emitter.removeListener(e4, t3);
              }, e3.prototype.start = function() {
                this._loadSegment(0), this._enableStatisticsReporter();
              }, e3.prototype._loadSegment = function(e4, t3) {
                this._currentSegmentIndex = e4;
                var i3 = this._mediaDataSource.segments[e4], a2 = this._ioctl = new Ne.default(i3, this._config, e4);
                a2.onError = this._onIOException.bind(this), a2.onSeeked = this._onIOSeeked.bind(this), a2.onComplete = this._onIOComplete.bind(this), a2.onRedirect = this._onIORedirect.bind(this), a2.onRecoveredEarlyEof = this._onIORecoveredEarlyEof.bind(this), t3 ? this._demuxer.bindDataSource(this._ioctl) : a2.onDataArrival = this._onInitChunkArrival.bind(this), a2.open(t3);
              }, e3.prototype.stop = function() {
                this._internalAbort(), this._disableStatisticsReporter();
              }, e3.prototype._internalAbort = function() {
                this._ioctl && (this._ioctl.destroy(), this._ioctl = null);
              }, e3.prototype.pause = function() {
                this._ioctl && this._ioctl.isWorking() && (this._ioctl.pause(), this._disableStatisticsReporter());
              }, e3.prototype.resume = function() {
                this._ioctl && this._ioctl.isPaused() && (this._ioctl.resume(), this._enableStatisticsReporter());
              }, e3.prototype.seek = function(e4) {
                if (null != this._mediaInfo && this._mediaInfo.isSeekable()) {
                  var t3 = this._searchSegmentIndexContains(e4);
                  if (t3 === this._currentSegmentIndex) {
                    var i3 = this._mediaInfo.segments[t3];
                    if (null == i3) this._pendingSeekTime = e4;
                    else {
                      var a2 = i3.getNearestKeyframe(e4);
                      this._remuxer.seek(a2.milliseconds), this._ioctl.seek(a2.fileposition), this._pendingResolveSeekPoint = a2.milliseconds;
                    }
                  } else {
                    var n2 = this._mediaInfo.segments[t3];
                    null == n2 ? (this._pendingSeekTime = e4, this._internalAbort(), this._remuxer.seek(), this._remuxer.insertDiscontinuity(), this._loadSegment(t3)) : (a2 = n2.getNearestKeyframe(e4), this._internalAbort(), this._remuxer.seek(e4), this._remuxer.insertDiscontinuity(), this._demuxer.resetMediaInfo(), this._demuxer.timestampBase = this._mediaDataSource.segments[t3].timestampBase, this._loadSegment(t3, a2.fileposition), this._pendingResolveSeekPoint = a2.milliseconds, this._reportSegmentMediaInfo(t3));
                  }
                  this._enableStatisticsReporter();
                }
              }, e3.prototype._searchSegmentIndexContains = function(e4) {
                for (var t3 = this._mediaDataSource.segments, i3 = t3.length - 1, a2 = 0; a2 < t3.length; a2++) if (e4 < t3[a2].timestampBase) {
                  i3 = a2 - 1;
                  break;
                }
                return i3;
              }, e3.prototype._onInitChunkArrival = function(e4, t3) {
                var i3 = this, a2 = 0;
                if (t3 > 0) this._demuxer.bindDataSource(this._ioctl), this._demuxer.timestampBase = this._mediaDataSource.segments[this._currentSegmentIndex].timestampBase, a2 = this._demuxer.parseChunks(e4, t3);
                else {
                  var n2 = null;
                  (n2 = D.probe(e4)).match && (this._setupFLVDemuxerRemuxer(n2), a2 = this._demuxer.parseChunks(e4, t3)), n2.match || n2.needMoreData || (n2 = we.probe(e4)).match && (this._setupTSDemuxerRemuxer(n2), a2 = this._demuxer.parseChunks(e4, t3)), n2.match || n2.needMoreData || (n2 = null, r.default.e(this.TAG, "Non MPEG-TS/FLV, Unsupported media type!"), Promise.resolve().then(function() {
                    i3._internalAbort();
                  }), this._emitter.emit(Ve.default.DEMUX_ERROR, m.default.FORMAT_UNSUPPORTED, "Non MPEG-TS/FLV, Unsupported media type!"));
                }
                return a2;
              }, e3.prototype._setupFLVDemuxerRemuxer = function(e4) {
                this._demuxer = new D(e4, this._config), this._remuxer || (this._remuxer = new Ue(this._config));
                var t3 = this._mediaDataSource;
                null == t3.duration || isNaN(t3.duration) || (this._demuxer.overridedDuration = t3.duration), "boolean" == typeof t3.hasAudio && (this._demuxer.overridedHasAudio = t3.hasAudio), "boolean" == typeof t3.hasVideo && (this._demuxer.overridedHasVideo = t3.hasVideo), this._demuxer.timestampBase = t3.segments[this._currentSegmentIndex].timestampBase, this._demuxer.onError = this._onDemuxException.bind(this), this._demuxer.onMediaInfo = this._onMediaInfo.bind(this), this._demuxer.onMetaDataArrived = this._onMetaDataArrived.bind(this), this._demuxer.onScriptDataArrived = this._onScriptDataArrived.bind(this), this._demuxer.onSeiArrived = this._onSEI.bind(this), this._remuxer.bindDataSource(this._demuxer.bindDataSource(this._ioctl)), this._remuxer.onInitSegment = this._onRemuxerInitSegmentArrival.bind(this), this._remuxer.onMediaSegment = this._onRemuxerMediaSegmentArrival.bind(this);
              }, e3.prototype._setupTSDemuxerRemuxer = function(e4) {
                var t3 = this._demuxer = new we(e4, this._config);
                this._remuxer || (this._remuxer = new Ue(this._config)), t3.onError = this._onDemuxException.bind(this), t3.onMediaInfo = this._onMediaInfo.bind(this), t3.onMetaDataArrived = this._onMetaDataArrived.bind(this), t3.onTimedID3Metadata = this._onTimedID3Metadata.bind(this), t3.onPGSSubtitleData = this._onPGSSubtitle.bind(this), t3.onSynchronousKLVMetadata = this._onSynchronousKLVMetadata.bind(this), t3.onAsynchronousKLVMetadata = this._onAsynchronousKLVMetadata.bind(this), t3.onSMPTE2038Metadata = this._onSMPTE2038Metadata.bind(this), t3.onSEI = this._onSEI.bind(this), t3.onSCTE35Metadata = this._onSCTE35Metadata.bind(this), t3.onPESPrivateDataDescriptor = this._onPESPrivateDataDescriptor.bind(this), t3.onPESPrivateData = this._onPESPrivateData.bind(this), this._remuxer.bindDataSource(this._demuxer), this._demuxer.bindDataSource(this._ioctl), this._remuxer.onInitSegment = this._onRemuxerInitSegmentArrival.bind(this), this._remuxer.onMediaSegment = this._onRemuxerMediaSegmentArrival.bind(this);
              }, e3.prototype._onMediaInfo = function(e4) {
                var t3 = this;
                null == this._mediaInfo && (this._mediaInfo = Object.assign({}, e4), this._mediaInfo.keyframesIndex = null, this._mediaInfo.segments = [], this._mediaInfo.segmentCount = this._mediaDataSource.segments.length, Object.setPrototypeOf(this._mediaInfo, s.default.prototype));
                var i3 = Object.assign({}, e4);
                Object.setPrototypeOf(i3, s.default.prototype), this._mediaInfo.segments[this._currentSegmentIndex] = i3, this._reportSegmentMediaInfo(this._currentSegmentIndex), null != this._pendingSeekTime && Promise.resolve().then(function() {
                  var e5 = t3._pendingSeekTime;
                  t3._pendingSeekTime = null, t3.seek(e5);
                });
              }, e3.prototype._onMetaDataArrived = function(e4) {
                this._emitter.emit(Ve.default.METADATA_ARRIVED, e4);
              }, e3.prototype._onScriptDataArrived = function(e4) {
                this._emitter.emit(Ve.default.SCRIPTDATA_ARRIVED, e4);
              }, e3.prototype._onTimedID3Metadata = function(e4) {
                var t3 = this._remuxer.getTimestampBase();
                null != t3 && (null != e4.pts && (e4.pts -= t3), null != e4.dts && (e4.dts -= t3), this._emitter.emit(Ve.default.TIMED_ID3_METADATA_ARRIVED, e4));
              }, e3.prototype._onPGSSubtitle = function(e4) {
                var t3 = this._remuxer.getTimestampBase();
                null != t3 && (null != e4.pts && (e4.pts -= t3), null != e4.dts && (e4.dts -= t3), this._emitter.emit(Ve.default.PGS_SUBTITLE_ARRIVED, e4));
              }, e3.prototype._onSynchronousKLVMetadata = function(e4) {
                var t3 = this._remuxer.getTimestampBase();
                null != t3 && (null != e4.pts && (e4.pts -= t3), null != e4.dts && (e4.dts -= t3), this._emitter.emit(Ve.default.SYNCHRONOUS_KLV_METADATA_ARRIVED, e4));
              }, e3.prototype._onAsynchronousKLVMetadata = function(e4) {
                this._emitter.emit(Ve.default.ASYNCHRONOUS_KLV_METADATA_ARRIVED, e4);
              }, e3.prototype._onSMPTE2038Metadata = function(e4) {
                var t3 = this._remuxer.getTimestampBase();
                null != t3 && (null != e4.pts && (e4.pts -= t3), null != e4.dts && (e4.dts -= t3), null != e4.nearest_pts && (e4.nearest_pts -= t3), this._emitter.emit(Ve.default.SMPTE2038_METADATA_ARRIVED, e4));
              }, e3.prototype._onSEI = function(e4) {
                var t3 = this._remuxer.getTimestampBase();
                null != t3 && (null != e4.pts && (e4.pts -= t3), this._emitter.emit(Ve.default.SEI_ARRIVED, e4));
              }, e3.prototype._onSCTE35Metadata = function(e4) {
                var t3 = this._remuxer.getTimestampBase();
                null != t3 && (null != e4.pts && (e4.pts -= t3), null != e4.nearest_pts && (e4.nearest_pts -= t3), this._emitter.emit(Ve.default.SCTE35_METADATA_ARRIVED, e4));
              }, e3.prototype._onPESPrivateDataDescriptor = function(e4) {
                this._emitter.emit(Ve.default.PES_PRIVATE_DATA_DESCRIPTOR, e4);
              }, e3.prototype._onPESPrivateData = function(e4) {
                var t3 = this._remuxer.getTimestampBase();
                null != t3 && (null != e4.pts && (e4.pts -= t3), null != e4.nearest_pts && (e4.nearest_pts -= t3), null != e4.dts && (e4.dts -= t3), this._emitter.emit(Ve.default.PES_PRIVATE_DATA_ARRIVED, e4));
              }, e3.prototype._onIOSeeked = function() {
                this._remuxer.insertDiscontinuity();
              }, e3.prototype._onIOComplete = function(e4) {
                var t3 = e4 + 1;
                t3 < this._mediaDataSource.segments.length ? (this._internalAbort(), this._remuxer && this._remuxer.flushStashedSamples(), this._loadSegment(t3)) : (this._remuxer && this._remuxer.flushStashedSamples(), this._emitter.emit(Ve.default.LOADING_COMPLETE), this._disableStatisticsReporter());
              }, e3.prototype._onIORedirect = function(e4) {
                var t3 = this._ioctl.extraData;
                this._mediaDataSource.segments[t3].redirectedURL = e4;
              }, e3.prototype._onIORecoveredEarlyEof = function() {
                this._emitter.emit(Ve.default.RECOVERED_EARLY_EOF);
              }, e3.prototype._onIOException = function(e4, t3) {
                r.default.e(this.TAG, "IOException: type = ".concat(e4, ", code = ").concat(t3.code, ", msg = ").concat(t3.msg)), this._emitter.emit(Ve.default.IO_ERROR, e4, t3), this._disableStatisticsReporter();
              }, e3.prototype._onDemuxException = function(e4, t3) {
                r.default.e(this.TAG, "DemuxException: type = ".concat(e4, ", info = ").concat(t3)), this._emitter.emit(Ve.default.DEMUX_ERROR, e4, t3);
              }, e3.prototype._onRemuxerInitSegmentArrival = function(e4, t3) {
                this._emitter.emit(Ve.default.INIT_SEGMENT, e4, t3);
              }, e3.prototype._onRemuxerMediaSegmentArrival = function(e4, t3) {
                if (null == this._pendingSeekTime && (this._emitter.emit(Ve.default.MEDIA_SEGMENT, e4, t3), null != this._pendingResolveSeekPoint && "video" === e4)) {
                  var i3 = t3.info.syncPoints, a2 = this._pendingResolveSeekPoint;
                  this._pendingResolveSeekPoint = null, o.default.safari && i3.length > 0 && i3[0].originalDts === a2 && (a2 = i3[0].pts), this._emitter.emit(Ve.default.RECOMMEND_SEEKPOINT, a2);
                }
              }, e3.prototype._enableStatisticsReporter = function() {
                null == this._statisticsReporter && (this._statisticsReporter = self.setInterval(this._reportStatisticsInfo.bind(this), this._config.statisticsInfoReportInterval));
              }, e3.prototype._disableStatisticsReporter = function() {
                this._statisticsReporter && (self.clearInterval(this._statisticsReporter), this._statisticsReporter = null);
              }, e3.prototype._reportSegmentMediaInfo = function(e4) {
                var t3 = this._mediaInfo.segments[e4], i3 = Object.assign({}, t3);
                i3.duration = this._mediaInfo.duration, i3.segmentCount = this._mediaInfo.segmentCount, delete i3.segments, delete i3.keyframesIndex, this._emitter.emit(Ve.default.MEDIA_INFO, i3);
              }, e3.prototype._reportStatisticsInfo = function() {
                var e4 = {};
                e4.url = this._ioctl.currentURL, e4.hasRedirect = this._ioctl.hasRedirect, e4.hasRedirect && (e4.redirectedURL = this._ioctl.currentRedirectedURL), e4.speed = this._ioctl.currentSpeed, e4.loaderType = this._ioctl.loaderType, e4.currentSegmentIndex = this._currentSegmentIndex, e4.totalSegmentCount = this._mediaDataSource.segments.length, this._emitter.emit(Ve.default.STATISTICS_INFO, e4);
              }, e3;
            })());
          }, 137: function(e2, t2, i2) {
            "use strict";
            i2.r(t2), i2(856);
            var a = i2(947), n = i2(811), r = i2(886), o = i2(726);
            t2.default = function(e3) {
              var t3 = null, i3 = function(t4, i4) {
                e3.postMessage({ msg: "logcat_callback", data: { type: t4, logcat: i4 } });
              }.bind(this);
              function s(t4, i4) {
                var a2 = { msg: o.default.INIT_SEGMENT, data: { type: t4, data: i4 } };
                e3.postMessage(a2, [i4.data]);
              }
              function d(t4, i4) {
                var a2 = { msg: o.default.MEDIA_SEGMENT, data: { type: t4, data: i4 } };
                e3.postMessage(a2, [i4.data]);
              }
              function _() {
                var t4 = { msg: o.default.LOADING_COMPLETE };
                e3.postMessage(t4);
              }
              function l() {
                var t4 = { msg: o.default.RECOVERED_EARLY_EOF };
                e3.postMessage(t4);
              }
              function u(t4) {
                var i4 = { msg: o.default.MEDIA_INFO, data: t4 };
                e3.postMessage(i4);
              }
              function c(t4) {
                var i4 = { msg: o.default.METADATA_ARRIVED, data: t4 };
                e3.postMessage(i4);
              }
              function h(t4) {
                var i4 = { msg: o.default.SCRIPTDATA_ARRIVED, data: t4 };
                e3.postMessage(i4);
              }
              function f(t4) {
                var i4 = { msg: o.default.TIMED_ID3_METADATA_ARRIVED, data: t4 };
                e3.postMessage(i4);
              }
              function p(t4) {
                var i4 = { msg: o.default.PGS_SUBTITLE_ARRIVED, data: t4 };
                e3.postMessage(i4);
              }
              function m(t4) {
                var i4 = { msg: o.default.SYNCHRONOUS_KLV_METADATA_ARRIVED, data: t4 };
                e3.postMessage(i4);
              }
              function g(t4) {
                var i4 = { msg: o.default.ASYNCHRONOUS_KLV_METADATA_ARRIVED, data: t4 };
                e3.postMessage(i4);
              }
              function v(t4) {
                var i4 = { msg: o.default.SMPTE2038_METADATA_ARRIVED, data: t4 };
                e3.postMessage(i4);
              }
              function y(t4) {
                var i4 = { msg: o.default.SEI_ARRIVED, data: t4 };
                e3.postMessage(i4);
              }
              function S(t4) {
                var i4 = { msg: o.default.SCTE35_METADATA_ARRIVED, data: t4 };
                e3.postMessage(i4);
              }
              function E(t4) {
                var i4 = { msg: o.default.PES_PRIVATE_DATA_DESCRIPTOR, data: t4 };
                e3.postMessage(i4);
              }
              function A(t4) {
                var i4 = { msg: o.default.PES_PRIVATE_DATA_ARRIVED, data: t4 };
                e3.postMessage(i4);
              }
              function b(t4) {
                var i4 = { msg: o.default.STATISTICS_INFO, data: t4 };
                e3.postMessage(i4);
              }
              function R(t4, i4) {
                e3.postMessage({ msg: o.default.IO_ERROR, data: { type: t4, info: i4 } });
              }
              function T(t4, i4) {
                e3.postMessage({ msg: o.default.DEMUX_ERROR, data: { type: t4, info: i4 } });
              }
              function L(t4) {
                e3.postMessage({ msg: o.default.RECOMMEND_SEEKPOINT, data: t4 });
              }
              n.default.install(), e3.addEventListener("message", function(n2) {
                switch (n2.data.cmd) {
                  case "init":
                    (t3 = new r.default(n2.data.param[0], n2.data.param[1])).on(o.default.IO_ERROR, R.bind(this)), t3.on(o.default.DEMUX_ERROR, T.bind(this)), t3.on(o.default.INIT_SEGMENT, s.bind(this)), t3.on(o.default.MEDIA_SEGMENT, d.bind(this)), t3.on(o.default.LOADING_COMPLETE, _.bind(this)), t3.on(o.default.RECOVERED_EARLY_EOF, l.bind(this)), t3.on(o.default.MEDIA_INFO, u.bind(this)), t3.on(o.default.METADATA_ARRIVED, c.bind(this)), t3.on(o.default.SCRIPTDATA_ARRIVED, h.bind(this)), t3.on(o.default.TIMED_ID3_METADATA_ARRIVED, f.bind(this)), t3.on(o.default.PGS_SUBTITLE_ARRIVED, p.bind(this)), t3.on(o.default.SYNCHRONOUS_KLV_METADATA_ARRIVED, m.bind(this)), t3.on(o.default.ASYNCHRONOUS_KLV_METADATA_ARRIVED, g.bind(this)), t3.on(o.default.SMPTE2038_METADATA_ARRIVED, v.bind(this)), t3.on(o.default.SEI_ARRIVED, y.bind(this)), t3.on(o.default.SCTE35_METADATA_ARRIVED, S.bind(this)), t3.on(o.default.PES_PRIVATE_DATA_DESCRIPTOR, E.bind(this)), t3.on(o.default.PES_PRIVATE_DATA_ARRIVED, A.bind(this)), t3.on(o.default.STATISTICS_INFO, b.bind(this)), t3.on(o.default.RECOMMEND_SEEKPOINT, L.bind(this));
                    break;
                  case "destroy":
                    t3 && (t3.destroy(), t3 = null), e3.postMessage({ msg: "destroyed" });
                    break;
                  case "start":
                    t3.start();
                    break;
                  case "stop":
                    t3.stop();
                    break;
                  case "seek":
                    t3.seek(n2.data.param);
                    break;
                  case "pause":
                    t3.pause();
                    break;
                  case "resume":
                    t3.resume();
                    break;
                  case "logging_config":
                    var k = n2.data.param;
                    a.default.applyConfig(k), true === k.enableCallback ? a.default.addLogListener(i3) : a.default.removeLogListener(i3);
                }
              });
            };
          }, 827: function(e2, t2, i2) {
            "use strict";
            i2.r(t2), t2.default = { OK: "OK", FORMAT_ERROR: "FormatError", FORMAT_UNSUPPORTED: "FormatUnsupported", CODEC_UNSUPPORTED: "CodecUnsupported" };
          }, 976: function(e2, t2, i2) {
            e2.exports = i2(311).default;
          }, 653: function(e2, t2, i2) {
            "use strict";
            i2.r(t2), i2.d(t2, { default: function() {
              return y;
            } });
            var a, n = i2(856), r = (function() {
              function e3() {
                this._firstCheckpoint = 0, this._lastCheckpoint = 0, this._intervalBytes = 0, this._totalBytes = 0, this._lastSecondBytes = 0, self.performance && self.performance.now ? this._now = self.performance.now.bind(self.performance) : this._now = Date.now;
              }
              return e3.prototype.reset = function() {
                this._firstCheckpoint = this._lastCheckpoint = 0, this._totalBytes = this._intervalBytes = 0, this._lastSecondBytes = 0;
              }, e3.prototype.addBytes = function(e4) {
                0 === this._firstCheckpoint ? (this._firstCheckpoint = this._now(), this._lastCheckpoint = this._firstCheckpoint, this._intervalBytes += e4, this._totalBytes += e4) : this._now() - this._lastCheckpoint < 1e3 ? (this._intervalBytes += e4, this._totalBytes += e4) : (this._lastSecondBytes = this._intervalBytes, this._intervalBytes = e4, this._totalBytes += e4, this._lastCheckpoint = this._now());
              }, Object.defineProperty(e3.prototype, "currentKBps", { get: function() {
                this.addBytes(0);
                var e4 = (this._now() - this._lastCheckpoint) / 1e3;
                return 0 == e4 && (e4 = 1), this._intervalBytes / e4 / 1024;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "lastSecondKBps", { get: function() {
                return this.addBytes(0), 0 !== this._lastSecondBytes ? this._lastSecondBytes / 1024 : this._now() - this._lastCheckpoint >= 500 ? this.currentKBps : 0;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "averageKBps", { get: function() {
                var e4 = (this._now() - this._firstCheckpoint) / 1e3;
                return this._totalBytes / e4 / 1024;
              }, enumerable: false, configurable: true }), e3;
            })(), o = i2(470), s = i2(994), d = i2(867), _ = (a = function(e3, t3) {
              return a = Object.setPrototypeOf || { __proto__: [] } instanceof Array && function(e4, t4) {
                e4.__proto__ = t4;
              } || function(e4, t4) {
                for (var i3 in t4) Object.prototype.hasOwnProperty.call(t4, i3) && (e4[i3] = t4[i3]);
              }, a(e3, t3);
            }, function(e3, t3) {
              if ("function" != typeof t3 && null !== t3) throw new TypeError("Class extends value " + String(t3) + " is not a constructor or null");
              function i3() {
                this.constructor = e3;
              }
              a(e3, t3), e3.prototype = null === t3 ? Object.create(t3) : (i3.prototype = t3.prototype, new i3());
            }), l = (function(e3) {
              function t3(t4, i3) {
                var a2 = e3.call(this, "fetch-stream-loader") || this;
                return a2.TAG = "FetchStreamLoader", a2._seekHandler = t4, a2._config = i3, a2._needStash = true, a2._requestAbort = false, a2._abortController = null, a2._contentLength = null, a2._receivedLength = 0, a2;
              }
              return _(t3, e3), t3.isSupported = function() {
                try {
                  var e4 = s.default.msedge && s.default.version.minor >= 15048, t4 = !s.default.msedge || e4;
                  return self.fetch && self.ReadableStream && t4;
                } catch (e5) {
                  return false;
                }
              }, t3.prototype.destroy = function() {
                this.isWorking() && this.abort(), e3.prototype.destroy.call(this);
              }, t3.prototype.open = function(e4, t4) {
                var i3 = this;
                this._dataSource = e4, this._range = t4;
                var a2 = e4.url;
                this._config.reuseRedirectedURL && null != e4.redirectedURL && (a2 = e4.redirectedURL);
                var n2 = this._seekHandler.getConfig(a2, t4), r2 = new self.Headers();
                if ("object" == typeof n2.headers) {
                  var s2 = n2.headers;
                  for (var _2 in s2) s2.hasOwnProperty(_2) && r2.append(_2, s2[_2]);
                }
                var l2 = { method: "GET", headers: r2, mode: "cors", cache: "default", referrerPolicy: "no-referrer-when-downgrade" };
                if ("object" == typeof this._config.headers) for (var _2 in this._config.headers) r2.append(_2, this._config.headers[_2]);
                false === e4.cors && (l2.mode = "same-origin"), e4.withCredentials && (l2.credentials = "include"), e4.referrerPolicy && (l2.referrerPolicy = e4.referrerPolicy), self.AbortController && (this._abortController = new self.AbortController(), l2.signal = this._abortController.signal), this._status = o.LoaderStatus.kConnecting, self.fetch(n2.url, l2).then(function(e5) {
                  if (i3._requestAbort) return i3._status = o.LoaderStatus.kIdle, void e5.body.cancel();
                  if (e5.ok && e5.status >= 200 && e5.status <= 299) {
                    if (e5.url !== n2.url && i3._onURLRedirect) {
                      var t5 = i3._seekHandler.removeURLParameters(e5.url);
                      i3._onURLRedirect(t5);
                    }
                    var a3 = e5.headers.get("Content-Length");
                    return null != a3 && (i3._contentLength = parseInt(a3), 0 !== i3._contentLength && i3._onContentLengthKnown && i3._onContentLengthKnown(i3._contentLength)), i3._pump.call(i3, e5.body.getReader());
                  }
                  if (i3._status = o.LoaderStatus.kError, !i3._onError) throw new d.RuntimeException("FetchStreamLoader: Http code invalid, " + e5.status + " " + e5.statusText);
                  i3._onError(o.LoaderErrors.HTTP_STATUS_CODE_INVALID, { code: e5.status, msg: e5.statusText });
                }).catch(function(e5) {
                  if (!i3._abortController || !i3._abortController.signal.aborted) {
                    if (i3._status = o.LoaderStatus.kError, !i3._onError) throw e5;
                    i3._onError(o.LoaderErrors.EXCEPTION, { code: -1, msg: e5.message });
                  }
                });
              }, t3.prototype.abort = function() {
                if (this._requestAbort = true, (this._status !== o.LoaderStatus.kBuffering || !s.default.chrome) && this._abortController) try {
                  this._abortController.abort();
                } catch (e4) {
                }
              }, t3.prototype._pump = function(e4) {
                var t4 = this;
                return e4.read().then(function(i3) {
                  if (i3.done) if (null !== t4._contentLength && t4._receivedLength < t4._contentLength) {
                    t4._status = o.LoaderStatus.kError;
                    var a2 = o.LoaderErrors.EARLY_EOF, n2 = { code: -1, msg: "Fetch stream meet Early-EOF" };
                    if (!t4._onError) throw new d.RuntimeException(n2.msg);
                    t4._onError(a2, n2);
                  } else t4._status = o.LoaderStatus.kComplete, t4._onComplete && t4._onComplete(t4._range.from, t4._range.from + t4._receivedLength - 1);
                  else {
                    if (t4._abortController && t4._abortController.signal.aborted) return void (t4._status = o.LoaderStatus.kComplete);
                    if (true === t4._requestAbort) return t4._status = o.LoaderStatus.kComplete, e4.cancel();
                    t4._status = o.LoaderStatus.kBuffering;
                    var r2 = i3.value.buffer, s2 = t4._range.from + t4._receivedLength;
                    t4._receivedLength += r2.byteLength, t4._onDataArrival && t4._onDataArrival(r2, s2, t4._receivedLength), t4._pump(e4);
                  }
                }).catch(function(e5) {
                  if (t4._abortController && t4._abortController.signal.aborted) t4._status = o.LoaderStatus.kComplete;
                  else if (11 !== e5.code || !s.default.msedge) {
                    t4._status = o.LoaderStatus.kError;
                    var i3 = 0, a2 = null;
                    if (19 !== e5.code && "network error" !== e5.message || !(null === t4._contentLength || null !== t4._contentLength && t4._receivedLength < t4._contentLength) ? (i3 = o.LoaderErrors.EXCEPTION, a2 = { code: e5.code, msg: e5.message }) : (i3 = o.LoaderErrors.EARLY_EOF, a2 = { code: e5.code, msg: "Fetch stream meet Early-EOF" }), !t4._onError) throw new d.RuntimeException(a2.msg);
                    t4._onError(i3, a2);
                  }
                });
              }, t3;
            })(o.BaseLoader), u = /* @__PURE__ */ (function() {
              var e3 = function(t3, i3) {
                return e3 = Object.setPrototypeOf || { __proto__: [] } instanceof Array && function(e4, t4) {
                  e4.__proto__ = t4;
                } || function(e4, t4) {
                  for (var i4 in t4) Object.prototype.hasOwnProperty.call(t4, i4) && (e4[i4] = t4[i4]);
                }, e3(t3, i3);
              };
              return function(t3, i3) {
                if ("function" != typeof i3 && null !== i3) throw new TypeError("Class extends value " + String(i3) + " is not a constructor or null");
                function a2() {
                  this.constructor = t3;
                }
                e3(t3, i3), t3.prototype = null === i3 ? Object.create(i3) : (a2.prototype = i3.prototype, new a2());
              };
            })(), c = (function(e3) {
              function t3(t4, i3) {
                var a2 = e3.call(this, "xhr-moz-chunked-loader") || this;
                return a2.TAG = "MozChunkedLoader", a2._seekHandler = t4, a2._config = i3, a2._needStash = true, a2._xhr = null, a2._requestAbort = false, a2._contentLength = null, a2._receivedLength = 0, a2;
              }
              return u(t3, e3), t3.isSupported = function() {
                try {
                  var e4 = new XMLHttpRequest();
                  return e4.open("GET", "https://example.com", true), e4.responseType = "moz-chunked-arraybuffer", "moz-chunked-arraybuffer" === e4.responseType;
                } catch (e5) {
                  return n.default.w("MozChunkedLoader", e5.message), false;
                }
              }, t3.prototype.destroy = function() {
                this.isWorking() && this.abort(), this._xhr && (this._xhr.onreadystatechange = null, this._xhr.onprogress = null, this._xhr.onloadend = null, this._xhr.onerror = null, this._xhr = null), e3.prototype.destroy.call(this);
              }, t3.prototype.open = function(e4, t4) {
                this._dataSource = e4, this._range = t4;
                var i3 = e4.url;
                this._config.reuseRedirectedURL && null != e4.redirectedURL && (i3 = e4.redirectedURL);
                var a2 = this._seekHandler.getConfig(i3, t4);
                this._requestURL = a2.url;
                var n2 = this._xhr = new XMLHttpRequest();
                if (n2.open("GET", a2.url, true), n2.responseType = "moz-chunked-arraybuffer", n2.onreadystatechange = this._onReadyStateChange.bind(this), n2.onprogress = this._onProgress.bind(this), n2.onloadend = this._onLoadEnd.bind(this), n2.onerror = this._onXhrError.bind(this), e4.withCredentials && (n2.withCredentials = true), "object" == typeof a2.headers) {
                  var r2 = a2.headers;
                  for (var s2 in r2) r2.hasOwnProperty(s2) && n2.setRequestHeader(s2, r2[s2]);
                }
                if ("object" == typeof this._config.headers) for (var s2 in r2 = this._config.headers) r2.hasOwnProperty(s2) && n2.setRequestHeader(s2, r2[s2]);
                this._status = o.LoaderStatus.kConnecting, n2.send();
              }, t3.prototype.abort = function() {
                this._requestAbort = true, this._xhr && this._xhr.abort(), this._status = o.LoaderStatus.kComplete;
              }, t3.prototype._onReadyStateChange = function(e4) {
                var t4 = e4.target;
                if (2 === t4.readyState) {
                  if (null != t4.responseURL && t4.responseURL !== this._requestURL && this._onURLRedirect) {
                    var i3 = this._seekHandler.removeURLParameters(t4.responseURL);
                    this._onURLRedirect(i3);
                  }
                  if (0 !== t4.status && (t4.status < 200 || t4.status > 299)) {
                    if (this._status = o.LoaderStatus.kError, !this._onError) throw new d.RuntimeException("MozChunkedLoader: Http code invalid, " + t4.status + " " + t4.statusText);
                    this._onError(o.LoaderErrors.HTTP_STATUS_CODE_INVALID, { code: t4.status, msg: t4.statusText });
                  } else this._status = o.LoaderStatus.kBuffering;
                }
              }, t3.prototype._onProgress = function(e4) {
                if (this._status !== o.LoaderStatus.kError) {
                  null === this._contentLength && null !== e4.total && 0 !== e4.total && (this._contentLength = e4.total, this._onContentLengthKnown && this._onContentLengthKnown(this._contentLength));
                  var t4 = e4.target.response, i3 = this._range.from + this._receivedLength;
                  this._receivedLength += t4.byteLength, this._onDataArrival && this._onDataArrival(t4, i3, this._receivedLength);
                }
              }, t3.prototype._onLoadEnd = function(e4) {
                true !== this._requestAbort ? this._status !== o.LoaderStatus.kError && (this._status = o.LoaderStatus.kComplete, this._onComplete && this._onComplete(this._range.from, this._range.from + this._receivedLength - 1)) : this._requestAbort = false;
              }, t3.prototype._onXhrError = function(e4) {
                this._status = o.LoaderStatus.kError;
                var t4 = 0, i3 = null;
                if (this._contentLength && e4.loaded < this._contentLength ? (t4 = o.LoaderErrors.EARLY_EOF, i3 = { code: -1, msg: "Moz-Chunked stream meet Early-Eof" }) : (t4 = o.LoaderErrors.EXCEPTION, i3 = { code: -1, msg: e4.constructor.name + " " + e4.type }), !this._onError) throw new d.RuntimeException(i3.msg);
                this._onError(t4, i3);
              }, t3;
            })(o.BaseLoader), h = /* @__PURE__ */ (function() {
              var e3 = function(t3, i3) {
                return e3 = Object.setPrototypeOf || { __proto__: [] } instanceof Array && function(e4, t4) {
                  e4.__proto__ = t4;
                } || function(e4, t4) {
                  for (var i4 in t4) Object.prototype.hasOwnProperty.call(t4, i4) && (e4[i4] = t4[i4]);
                }, e3(t3, i3);
              };
              return function(t3, i3) {
                if ("function" != typeof i3 && null !== i3) throw new TypeError("Class extends value " + String(i3) + " is not a constructor or null");
                function a2() {
                  this.constructor = t3;
                }
                e3(t3, i3), t3.prototype = null === i3 ? Object.create(i3) : (a2.prototype = i3.prototype, new a2());
              };
            })(), f = (function(e3) {
              function t3(t4, i3) {
                var a2 = e3.call(this, "xhr-range-loader") || this;
                return a2.TAG = "RangeLoader", a2._seekHandler = t4, a2._config = i3, a2._needStash = false, a2._chunkSizeKBList = [128, 256, 384, 512, 768, 1024, 1536, 2048, 3072, 4096, 5120, 6144, 7168, 8192], a2._currentChunkSizeKB = 384, a2._currentSpeedNormalized = 0, a2._zeroSpeedChunkCount = 0, a2._xhr = null, a2._speedSampler = new r(), a2._requestAbort = false, a2._waitForTotalLength = false, a2._totalLengthReceived = false, a2._currentRequestURL = null, a2._currentRedirectedURL = null, a2._currentRequestRange = null, a2._totalLength = null, a2._contentLength = null, a2._receivedLength = 0, a2._lastTimeLoaded = 0, a2;
              }
              return h(t3, e3), t3.isSupported = function() {
                try {
                  var e4 = new XMLHttpRequest();
                  return e4.open("GET", "https://example.com", true), e4.responseType = "arraybuffer", "arraybuffer" === e4.responseType;
                } catch (e5) {
                  return n.default.w("RangeLoader", e5.message), false;
                }
              }, t3.prototype.destroy = function() {
                this.isWorking() && this.abort(), this._xhr && (this._xhr.onreadystatechange = null, this._xhr.onprogress = null, this._xhr.onload = null, this._xhr.onerror = null, this._xhr = null), e3.prototype.destroy.call(this);
              }, Object.defineProperty(t3.prototype, "currentSpeed", { get: function() {
                return this._speedSampler.lastSecondKBps;
              }, enumerable: false, configurable: true }), t3.prototype.open = function(e4, t4) {
                this._dataSource = e4, this._range = t4, this._status = o.LoaderStatus.kConnecting;
                var i3 = false;
                null != this._dataSource.filesize && 0 !== this._dataSource.filesize && (i3 = true, this._totalLength = this._dataSource.filesize), this._totalLengthReceived || i3 ? this._openSubRange() : (this._waitForTotalLength = true, this._internalOpen(this._dataSource, { from: 0, to: -1 }));
              }, t3.prototype._openSubRange = function() {
                var e4 = 1024 * this._currentChunkSizeKB, t4 = this._range.from + this._receivedLength, i3 = t4 + e4;
                null != this._contentLength && i3 - this._range.from >= this._contentLength && (i3 = this._range.from + this._contentLength - 1), this._currentRequestRange = { from: t4, to: i3 }, this._internalOpen(this._dataSource, this._currentRequestRange);
              }, t3.prototype._internalOpen = function(e4, t4) {
                this._lastTimeLoaded = 0;
                var i3 = e4.url;
                this._config.reuseRedirectedURL && (null != this._currentRedirectedURL ? i3 = this._currentRedirectedURL : null != e4.redirectedURL && (i3 = e4.redirectedURL));
                var a2 = this._seekHandler.getConfig(i3, t4);
                this._currentRequestURL = a2.url;
                var n2 = this._xhr = new XMLHttpRequest();
                if (n2.open("GET", a2.url, true), n2.responseType = "arraybuffer", n2.onreadystatechange = this._onReadyStateChange.bind(this), n2.onprogress = this._onProgress.bind(this), n2.onload = this._onLoad.bind(this), n2.onerror = this._onXhrError.bind(this), e4.withCredentials && (n2.withCredentials = true), "object" == typeof a2.headers) {
                  var r2 = a2.headers;
                  for (var o2 in r2) r2.hasOwnProperty(o2) && n2.setRequestHeader(o2, r2[o2]);
                }
                if ("object" == typeof this._config.headers) for (var o2 in r2 = this._config.headers) r2.hasOwnProperty(o2) && n2.setRequestHeader(o2, r2[o2]);
                n2.send();
              }, t3.prototype.abort = function() {
                this._requestAbort = true, this._internalAbort(), this._status = o.LoaderStatus.kComplete;
              }, t3.prototype._internalAbort = function() {
                this._xhr && (this._xhr.onreadystatechange = null, this._xhr.onprogress = null, this._xhr.onload = null, this._xhr.onerror = null, this._xhr.abort(), this._xhr = null);
              }, t3.prototype._onReadyStateChange = function(e4) {
                var t4 = e4.target;
                if (2 === t4.readyState) {
                  if (null != t4.responseURL) {
                    var i3 = this._seekHandler.removeURLParameters(t4.responseURL);
                    t4.responseURL !== this._currentRequestURL && i3 !== this._currentRedirectedURL && (this._currentRedirectedURL = i3, this._onURLRedirect && this._onURLRedirect(i3));
                  }
                  if (t4.status >= 200 && t4.status <= 299) {
                    if (this._waitForTotalLength) return;
                    this._status = o.LoaderStatus.kBuffering;
                  } else {
                    if (this._status = o.LoaderStatus.kError, !this._onError) throw new d.RuntimeException("RangeLoader: Http code invalid, " + t4.status + " " + t4.statusText);
                    this._onError(o.LoaderErrors.HTTP_STATUS_CODE_INVALID, { code: t4.status, msg: t4.statusText });
                  }
                }
              }, t3.prototype._onProgress = function(e4) {
                if (this._status !== o.LoaderStatus.kError) {
                  if (null === this._contentLength) {
                    var t4 = false;
                    if (this._waitForTotalLength) {
                      this._waitForTotalLength = false, this._totalLengthReceived = true, t4 = true;
                      var i3 = e4.total;
                      this._internalAbort(), null != i3 & 0 !== i3 && (this._totalLength = i3);
                    }
                    if (-1 === this._range.to ? this._contentLength = this._totalLength - this._range.from : this._contentLength = this._range.to - this._range.from + 1, t4) return void this._openSubRange();
                    this._onContentLengthKnown && this._onContentLengthKnown(this._contentLength);
                  }
                  var a2 = e4.loaded - this._lastTimeLoaded;
                  this._lastTimeLoaded = e4.loaded, this._speedSampler.addBytes(a2);
                }
              }, t3.prototype._normalizeSpeed = function(e4) {
                var t4 = this._chunkSizeKBList, i3 = t4.length - 1, a2 = 0, n2 = 0, r2 = i3;
                if (e4 < t4[0]) return t4[0];
                for (; n2 <= r2; ) {
                  if ((a2 = n2 + Math.floor((r2 - n2) / 2)) === i3 || e4 >= t4[a2] && e4 < t4[a2 + 1]) return t4[a2];
                  t4[a2] < e4 ? n2 = a2 + 1 : r2 = a2 - 1;
                }
              }, t3.prototype._onLoad = function(e4) {
                if (this._status !== o.LoaderStatus.kError) if (this._waitForTotalLength) this._waitForTotalLength = false;
                else {
                  this._lastTimeLoaded = 0;
                  var t4 = this._speedSampler.lastSecondKBps;
                  if (0 === t4 && (this._zeroSpeedChunkCount++, this._zeroSpeedChunkCount >= 3 && (t4 = this._speedSampler.currentKBps)), 0 !== t4) {
                    var i3 = this._normalizeSpeed(t4);
                    this._currentSpeedNormalized !== i3 && (this._currentSpeedNormalized = i3, this._currentChunkSizeKB = i3);
                  }
                  var a2 = e4.target.response, n2 = this._range.from + this._receivedLength;
                  this._receivedLength += a2.byteLength;
                  var r2 = false;
                  null != this._contentLength && this._receivedLength < this._contentLength ? this._openSubRange() : r2 = true, this._onDataArrival && this._onDataArrival(a2, n2, this._receivedLength), r2 && (this._status = o.LoaderStatus.kComplete, this._onComplete && this._onComplete(this._range.from, this._range.from + this._receivedLength - 1));
                }
              }, t3.prototype._onXhrError = function(e4) {
                this._status = o.LoaderStatus.kError;
                var t4 = 0, i3 = null;
                if (this._contentLength && this._receivedLength > 0 && this._receivedLength < this._contentLength ? (t4 = o.LoaderErrors.EARLY_EOF, i3 = { code: -1, msg: "RangeLoader meet Early-Eof" }) : (t4 = o.LoaderErrors.EXCEPTION, i3 = { code: -1, msg: e4.constructor.name + " " + e4.type }), !this._onError) throw new d.RuntimeException(i3.msg);
                this._onError(t4, i3);
              }, t3;
            })(o.BaseLoader), p = /* @__PURE__ */ (function() {
              var e3 = function(t3, i3) {
                return e3 = Object.setPrototypeOf || { __proto__: [] } instanceof Array && function(e4, t4) {
                  e4.__proto__ = t4;
                } || function(e4, t4) {
                  for (var i4 in t4) Object.prototype.hasOwnProperty.call(t4, i4) && (e4[i4] = t4[i4]);
                }, e3(t3, i3);
              };
              return function(t3, i3) {
                if ("function" != typeof i3 && null !== i3) throw new TypeError("Class extends value " + String(i3) + " is not a constructor or null");
                function a2() {
                  this.constructor = t3;
                }
                e3(t3, i3), t3.prototype = null === i3 ? Object.create(i3) : (a2.prototype = i3.prototype, new a2());
              };
            })(), m = (function(e3) {
              function t3() {
                var t4 = e3.call(this, "websocket-loader") || this;
                return t4.TAG = "WebSocketLoader", t4._needStash = true, t4._ws = null, t4._requestAbort = false, t4._receivedLength = 0, t4;
              }
              return p(t3, e3), t3.isSupported = function() {
                try {
                  return void 0 !== self.WebSocket;
                } catch (e4) {
                  return false;
                }
              }, t3.prototype.destroy = function() {
                this._ws && this.abort(), e3.prototype.destroy.call(this);
              }, t3.prototype.open = function(e4) {
                try {
                  var t4 = this._ws = new self.WebSocket(e4.url);
                  t4.binaryType = "arraybuffer", t4.onopen = this._onWebSocketOpen.bind(this), t4.onclose = this._onWebSocketClose.bind(this), t4.onmessage = this._onWebSocketMessage.bind(this), t4.onerror = this._onWebSocketError.bind(this), this._status = o.LoaderStatus.kConnecting;
                } catch (e5) {
                  this._status = o.LoaderStatus.kError;
                  var i3 = { code: e5.code, msg: e5.message };
                  if (!this._onError) throw new d.RuntimeException(i3.msg);
                  this._onError(o.LoaderErrors.EXCEPTION, i3);
                }
              }, t3.prototype.abort = function() {
                var e4 = this._ws;
                !e4 || 0 !== e4.readyState && 1 !== e4.readyState || (this._requestAbort = true, e4.close()), this._ws = null, this._status = o.LoaderStatus.kComplete;
              }, t3.prototype._onWebSocketOpen = function(e4) {
                this._status = o.LoaderStatus.kBuffering;
              }, t3.prototype._onWebSocketClose = function(e4) {
                true !== this._requestAbort ? (this._status = o.LoaderStatus.kComplete, this._onComplete && this._onComplete(0, this._receivedLength - 1)) : this._requestAbort = false;
              }, t3.prototype._onWebSocketMessage = function(e4) {
                var t4 = this;
                if (e4.data instanceof ArrayBuffer) this._dispatchArrayBuffer(e4.data);
                else if (e4.data instanceof Blob) {
                  var i3 = new FileReader();
                  i3.onload = function() {
                    t4._dispatchArrayBuffer(i3.result);
                  }, i3.readAsArrayBuffer(e4.data);
                } else {
                  this._status = o.LoaderStatus.kError;
                  var a2 = { code: -1, msg: "Unsupported WebSocket message type: " + e4.data.constructor.name };
                  if (!this._onError) throw new d.RuntimeException(a2.msg);
                  this._onError(o.LoaderErrors.EXCEPTION, a2);
                }
              }, t3.prototype._dispatchArrayBuffer = function(e4) {
                var t4 = e4, i3 = this._receivedLength;
                this._receivedLength += t4.byteLength, this._onDataArrival && this._onDataArrival(t4, i3, this._receivedLength);
              }, t3.prototype._onWebSocketError = function(e4) {
                this._status = o.LoaderStatus.kError;
                var t4 = { code: e4.code, msg: e4.message };
                if (!this._onError) throw new d.RuntimeException(t4.msg);
                this._onError(o.LoaderErrors.EXCEPTION, t4);
              }, t3;
            })(o.BaseLoader), g = (function() {
              function e3(e4) {
                this._zeroStart = e4 || false;
              }
              return e3.prototype.getConfig = function(e4, t3) {
                var i3 = {};
                if (0 !== t3.from || -1 !== t3.to) {
                  var a2;
                  a2 = -1 !== t3.to ? "bytes=".concat(t3.from.toString(), "-").concat(t3.to.toString()) : "bytes=".concat(t3.from.toString(), "-"), i3.Range = a2;
                } else this._zeroStart && (i3.Range = "bytes=0-");
                return { url: e4, headers: i3 };
              }, e3.prototype.removeURLParameters = function(e4) {
                return e4;
              }, e3;
            })(), v = (function() {
              function e3(e4, t3) {
                this._startName = e4, this._endName = t3;
              }
              return e3.prototype.getConfig = function(e4, t3) {
                var i3 = e4;
                if (0 !== t3.from || -1 !== t3.to) {
                  var a2 = true;
                  -1 === i3.indexOf("?") && (i3 += "?", a2 = false), a2 && (i3 += "&"), i3 += "".concat(this._startName, "=").concat(t3.from.toString()), -1 !== t3.to && (i3 += "&".concat(this._endName, "=").concat(t3.to.toString()));
                }
                return { url: i3, headers: {} };
              }, e3.prototype.removeURLParameters = function(e4) {
                var t3 = e4.split("?")[0], i3 = void 0, a2 = e4.indexOf("?");
                -1 !== a2 && (i3 = e4.substring(a2 + 1));
                var n2 = "";
                if (null != i3 && i3.length > 0) for (var r2 = i3.split("&"), o2 = 0; o2 < r2.length; o2++) {
                  var s2 = r2[o2].split("="), d2 = o2 > 0;
                  s2[0] !== this._startName && s2[0] !== this._endName && (d2 && (n2 += "&"), n2 += r2[o2]);
                }
                return 0 === n2.length ? t3 : t3 + "?" + n2;
              }, e3;
            })(), y = (function() {
              function e3(e4, t3, i3) {
                this.TAG = "IOController", this._config = t3, this._extraData = i3, this._stashInitialSize = 65536, null != t3.stashInitialSize && t3.stashInitialSize > 0 && (this._stashInitialSize = t3.stashInitialSize), this._stashUsed = 0, this._stashSize = this._stashInitialSize, this._bufferSize = Math.max(this._stashSize, 3145728), this._stashBuffer = new ArrayBuffer(this._bufferSize), this._stashByteStart = 0, this._enableStash = true, false === t3.enableStashBuffer && (this._enableStash = false), this._loader = null, this._loaderClass = null, this._seekHandler = null, this._dataSource = e4, this._isWebSocketURL = /wss?:\/\/(.+?)/.test(e4.url), this._refTotalLength = e4.filesize ? e4.filesize : null, this._totalLength = this._refTotalLength, this._fullRequestFlag = false, this._currentRange = null, this._redirectedURL = null, this._speedNormalized = 0, this._speedSampler = new r(), this._speedNormalizeList = [32, 64, 96, 128, 192, 256, 384, 512, 768, 1024, 1536, 2048, 3072, 4096], this._isEarlyEofReconnecting = false, this._paused = false, this._resumeFrom = 0, this._onDataArrival = null, this._onSeeked = null, this._onError = null, this._onComplete = null, this._onRedirect = null, this._onRecoveredEarlyEof = null, this._selectSeekHandler(), this._selectLoader(), this._createLoader();
              }
              return e3.prototype.destroy = function() {
                this._loader.isWorking() && this._loader.abort(), this._loader.destroy(), this._loader = null, this._loaderClass = null, this._dataSource = null, this._stashBuffer = null, this._stashUsed = this._stashSize = this._bufferSize = this._stashByteStart = 0, this._currentRange = null, this._speedSampler = null, this._isEarlyEofReconnecting = false, this._onDataArrival = null, this._onSeeked = null, this._onError = null, this._onComplete = null, this._onRedirect = null, this._onRecoveredEarlyEof = null, this._extraData = null;
              }, e3.prototype.isWorking = function() {
                return this._loader && this._loader.isWorking() && !this._paused;
              }, e3.prototype.isPaused = function() {
                return this._paused;
              }, Object.defineProperty(e3.prototype, "status", { get: function() {
                return this._loader.status;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "extraData", { get: function() {
                return this._extraData;
              }, set: function(e4) {
                this._extraData = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "onDataArrival", { get: function() {
                return this._onDataArrival;
              }, set: function(e4) {
                this._onDataArrival = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "onSeeked", { get: function() {
                return this._onSeeked;
              }, set: function(e4) {
                this._onSeeked = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "onError", { get: function() {
                return this._onError;
              }, set: function(e4) {
                this._onError = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "onComplete", { get: function() {
                return this._onComplete;
              }, set: function(e4) {
                this._onComplete = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "onRedirect", { get: function() {
                return this._onRedirect;
              }, set: function(e4) {
                this._onRedirect = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "onRecoveredEarlyEof", { get: function() {
                return this._onRecoveredEarlyEof;
              }, set: function(e4) {
                this._onRecoveredEarlyEof = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "currentURL", { get: function() {
                return this._dataSource.url;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "hasRedirect", { get: function() {
                return null != this._redirectedURL || null != this._dataSource.redirectedURL;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "currentRedirectedURL", { get: function() {
                return this._redirectedURL || this._dataSource.redirectedURL;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "currentSpeed", { get: function() {
                return this._loaderClass === f ? this._loader.currentSpeed : this._speedSampler.lastSecondKBps;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "loaderType", { get: function() {
                return this._loader.type;
              }, enumerable: false, configurable: true }), e3.prototype._selectSeekHandler = function() {
                var e4 = this._config;
                if ("range" === e4.seekType) this._seekHandler = new g(this._config.rangeLoadZeroStart);
                else if ("param" === e4.seekType) {
                  var t3 = e4.seekParamStart || "bstart", i3 = e4.seekParamEnd || "bend";
                  this._seekHandler = new v(t3, i3);
                } else {
                  if ("custom" !== e4.seekType) throw new d.InvalidArgumentException("Invalid seekType in config: ".concat(e4.seekType));
                  if ("function" != typeof e4.customSeekHandler) throw new d.InvalidArgumentException("Custom seekType specified in config but invalid customSeekHandler!");
                  this._seekHandler = new e4.customSeekHandler();
                }
              }, e3.prototype._selectLoader = function() {
                if (null != this._config.customLoader) this._loaderClass = this._config.customLoader;
                else if (this._isWebSocketURL) this._loaderClass = m;
                else if (l.isSupported()) this._loaderClass = l;
                else if (c.isSupported()) this._loaderClass = c;
                else {
                  if (!f.isSupported()) throw new d.RuntimeException("Your browser doesn't support xhr with arraybuffer responseType!");
                  this._loaderClass = f;
                }
              }, e3.prototype._createLoader = function() {
                this._loader = new this._loaderClass(this._seekHandler, this._config), false === this._loader.needStashBuffer && (this._enableStash = false), this._loader.onContentLengthKnown = this._onContentLengthKnown.bind(this), this._loader.onURLRedirect = this._onURLRedirect.bind(this), this._loader.onDataArrival = this._onLoaderChunkArrival.bind(this), this._loader.onComplete = this._onLoaderComplete.bind(this), this._loader.onError = this._onLoaderError.bind(this);
              }, e3.prototype.open = function(e4) {
                this._currentRange = { from: 0, to: -1 }, e4 && (this._currentRange.from = e4), this._speedSampler.reset(), e4 || (this._fullRequestFlag = true), this._loader.open(this._dataSource, Object.assign({}, this._currentRange));
              }, e3.prototype.abort = function() {
                this._loader.abort(), this._paused && (this._paused = false, this._resumeFrom = 0);
              }, e3.prototype.pause = function() {
                this.isWorking() && (this._loader.abort(), 0 !== this._stashUsed ? (this._resumeFrom = this._stashByteStart, this._currentRange.to = this._stashByteStart - 1) : this._resumeFrom = this._currentRange.to + 1, this._stashUsed = 0, this._stashByteStart = 0, this._paused = true);
              }, e3.prototype.resume = function() {
                if (this._paused) {
                  this._paused = false;
                  var e4 = this._resumeFrom;
                  this._resumeFrom = 0, this._internalSeek(e4, true);
                }
              }, e3.prototype.seek = function(e4) {
                this._paused = false, this._stashUsed = 0, this._stashByteStart = 0, this._internalSeek(e4, true);
              }, e3.prototype._internalSeek = function(e4, t3) {
                this._loader.isWorking() && this._loader.abort(), this._flushStashBuffer(t3), this._loader.destroy(), this._loader = null;
                var i3 = { from: e4, to: -1 };
                this._currentRange = { from: i3.from, to: -1 }, this._speedSampler.reset(), this._stashSize = this._stashInitialSize, this._createLoader(), this._loader.open(this._dataSource, i3), this._onSeeked && this._onSeeked();
              }, e3.prototype.updateUrl = function(e4) {
                if (!e4 || "string" != typeof e4 || 0 === e4.length) throw new d.InvalidArgumentException("Url must be a non-empty string!");
                this._dataSource.url = e4;
              }, e3.prototype._expandBuffer = function(e4) {
                for (var t3 = this._stashSize; t3 + 1048576 < e4; ) t3 *= 2;
                if ((t3 += 1048576) !== this._bufferSize) {
                  var i3 = new ArrayBuffer(t3);
                  if (this._stashUsed > 0) {
                    var a2 = new Uint8Array(this._stashBuffer, 0, this._stashUsed);
                    new Uint8Array(i3, 0, t3).set(a2, 0);
                  }
                  this._stashBuffer = i3, this._bufferSize = t3;
                }
              }, e3.prototype._normalizeSpeed = function(e4) {
                var t3 = this._speedNormalizeList, i3 = t3.length - 1, a2 = 0, n2 = 0, r2 = i3;
                if (e4 < t3[0]) return t3[0];
                for (; n2 <= r2; ) {
                  if ((a2 = n2 + Math.floor((r2 - n2) / 2)) === i3 || e4 >= t3[a2] && e4 < t3[a2 + 1]) return t3[a2];
                  t3[a2] < e4 ? n2 = a2 + 1 : r2 = a2 - 1;
                }
              }, e3.prototype._adjustStashSize = function(e4) {
                var t3 = 0;
                (t3 = this._config.isLive ? e4 / 8 : e4 < 512 ? e4 : e4 >= 512 && e4 <= 1024 ? Math.floor(1.5 * e4) : 2 * e4) > 8192 && (t3 = 8192);
                var i3 = 1024 * t3 + 1048576;
                this._bufferSize < i3 && this._expandBuffer(i3), this._stashSize = 1024 * t3;
              }, e3.prototype._dispatchChunks = function(e4, t3) {
                return this._currentRange.to = t3 + e4.byteLength - 1, this._onDataArrival(e4, t3);
              }, e3.prototype._onURLRedirect = function(e4) {
                this._redirectedURL = e4, this._onRedirect && this._onRedirect(e4);
              }, e3.prototype._onContentLengthKnown = function(e4) {
                e4 && this._fullRequestFlag && (this._totalLength = e4, this._fullRequestFlag = false);
              }, e3.prototype._onLoaderChunkArrival = function(e4, t3, i3) {
                if (!this._onDataArrival) throw new d.IllegalStateException("IOController: No existing consumer (onDataArrival) callback!");
                if (!this._paused) {
                  this._isEarlyEofReconnecting && (this._isEarlyEofReconnecting = false, this._onRecoveredEarlyEof && this._onRecoveredEarlyEof()), this._speedSampler.addBytes(e4.byteLength);
                  var a2 = this._speedSampler.lastSecondKBps;
                  if (0 !== a2) {
                    var n2 = this._normalizeSpeed(a2);
                    this._speedNormalized !== n2 && (this._speedNormalized = n2, this._adjustStashSize(n2));
                  }
                  if (this._enableStash) if (0 === this._stashUsed && 0 === this._stashByteStart && (this._stashByteStart = t3), this._stashUsed + e4.byteLength <= this._stashSize) (s2 = new Uint8Array(this._stashBuffer, 0, this._stashSize)).set(new Uint8Array(e4), this._stashUsed), this._stashUsed += e4.byteLength;
                  else if (s2 = new Uint8Array(this._stashBuffer, 0, this._bufferSize), this._stashUsed > 0) {
                    var r2 = this._stashBuffer.slice(0, this._stashUsed);
                    (_2 = this._dispatchChunks(r2, this._stashByteStart)) < r2.byteLength ? _2 > 0 && (l2 = new Uint8Array(r2, _2), s2.set(l2, 0), this._stashUsed = l2.byteLength, this._stashByteStart += _2) : (this._stashUsed = 0, this._stashByteStart += _2), this._stashUsed + e4.byteLength > this._bufferSize && (this._expandBuffer(this._stashUsed + e4.byteLength), s2 = new Uint8Array(this._stashBuffer, 0, this._bufferSize)), s2.set(new Uint8Array(e4), this._stashUsed), this._stashUsed += e4.byteLength;
                  } else (_2 = this._dispatchChunks(e4, t3)) < e4.byteLength && ((o2 = e4.byteLength - _2) > this._bufferSize && (this._expandBuffer(o2), s2 = new Uint8Array(this._stashBuffer, 0, this._bufferSize)), s2.set(new Uint8Array(e4, _2), 0), this._stashUsed += o2, this._stashByteStart = t3 + _2);
                  else if (0 === this._stashUsed) {
                    var o2;
                    (_2 = this._dispatchChunks(e4, t3)) < e4.byteLength && ((o2 = e4.byteLength - _2) > this._bufferSize && this._expandBuffer(o2), (s2 = new Uint8Array(this._stashBuffer, 0, this._bufferSize)).set(new Uint8Array(e4, _2), 0), this._stashUsed += o2, this._stashByteStart = t3 + _2);
                  } else {
                    var s2, _2;
                    if (this._stashUsed + e4.byteLength > this._bufferSize && this._expandBuffer(this._stashUsed + e4.byteLength), (s2 = new Uint8Array(this._stashBuffer, 0, this._bufferSize)).set(new Uint8Array(e4), this._stashUsed), this._stashUsed += e4.byteLength, (_2 = this._dispatchChunks(this._stashBuffer.slice(0, this._stashUsed), this._stashByteStart)) < this._stashUsed && _2 > 0) {
                      var l2 = new Uint8Array(this._stashBuffer, _2);
                      s2.set(l2, 0);
                    }
                    this._stashUsed -= _2, this._stashByteStart += _2;
                  }
                }
              }, e3.prototype._flushStashBuffer = function(e4) {
                if (this._stashUsed > 0) {
                  var t3 = this._stashBuffer.slice(0, this._stashUsed), i3 = this._dispatchChunks(t3, this._stashByteStart), a2 = t3.byteLength - i3;
                  if (i3 < t3.byteLength) {
                    if (!e4) {
                      if (i3 > 0) {
                        var r2 = new Uint8Array(this._stashBuffer, 0, this._bufferSize), o2 = new Uint8Array(t3, i3);
                        r2.set(o2, 0), this._stashUsed = o2.byteLength, this._stashByteStart += i3;
                      }
                      return 0;
                    }
                    n.default.w(this.TAG, "".concat(a2, " bytes unconsumed data remain when flush buffer, dropped"));
                  }
                  return this._stashUsed = 0, this._stashByteStart = 0, a2;
                }
                return 0;
              }, e3.prototype._onLoaderComplete = function(e4, t3) {
                this._flushStashBuffer(true), this._onComplete && this._onComplete(this._extraData);
              }, e3.prototype._onLoaderError = function(e4, t3) {
                switch (n.default.e(this.TAG, "Loader error, code = ".concat(t3.code, ", msg = ").concat(t3.msg)), this._flushStashBuffer(false), this._isEarlyEofReconnecting && (this._isEarlyEofReconnecting = false, e4 = o.LoaderErrors.UNRECOVERABLE_EARLY_EOF), e4) {
                  case o.LoaderErrors.EARLY_EOF:
                    if (!this._config.isLive && this._totalLength) {
                      var i3 = this._currentRange.to + 1;
                      return void (i3 < this._totalLength && (n.default.w(this.TAG, "Connection lost, trying reconnect..."), this._isEarlyEofReconnecting = true, this._internalSeek(i3, false)));
                    }
                    e4 = o.LoaderErrors.UNRECOVERABLE_EARLY_EOF;
                  case o.LoaderErrors.UNRECOVERABLE_EARLY_EOF:
                  case o.LoaderErrors.CONNECTING_TIMEOUT:
                  case o.LoaderErrors.HTTP_STATUS_CODE_INVALID:
                  case o.LoaderErrors.EXCEPTION:
                }
                if (!this._onError) throw new d.RuntimeException("IOException: " + t3.msg);
                this._onError(e4, t3);
              }, e3;
            })();
          }, 470: function(e2, t2, i2) {
            "use strict";
            i2.r(t2), i2.d(t2, { BaseLoader: function() {
              return o;
            }, LoaderErrors: function() {
              return r;
            }, LoaderStatus: function() {
              return n;
            } });
            var a = i2(867), n = { kIdle: 0, kConnecting: 1, kBuffering: 2, kError: 3, kComplete: 4 }, r = { OK: "OK", EXCEPTION: "Exception", HTTP_STATUS_CODE_INVALID: "HttpStatusCodeInvalid", CONNECTING_TIMEOUT: "ConnectingTimeout", EARLY_EOF: "EarlyEof", UNRECOVERABLE_EARLY_EOF: "UnrecoverableEarlyEof" }, o = (function() {
              function e3(e4) {
                this._type = e4 || "undefined", this._status = n.kIdle, this._needStash = false, this._onContentLengthKnown = null, this._onURLRedirect = null, this._onDataArrival = null, this._onError = null, this._onComplete = null;
              }
              return e3.prototype.destroy = function() {
                this._status = n.kIdle, this._onContentLengthKnown = null, this._onURLRedirect = null, this._onDataArrival = null, this._onError = null, this._onComplete = null;
              }, e3.prototype.isWorking = function() {
                return this._status === n.kConnecting || this._status === n.kBuffering;
              }, Object.defineProperty(e3.prototype, "type", { get: function() {
                return this._type;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "status", { get: function() {
                return this._status;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "needStashBuffer", { get: function() {
                return this._needStash;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "onContentLengthKnown", { get: function() {
                return this._onContentLengthKnown;
              }, set: function(e4) {
                this._onContentLengthKnown = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "onURLRedirect", { get: function() {
                return this._onURLRedirect;
              }, set: function(e4) {
                this._onURLRedirect = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "onDataArrival", { get: function() {
                return this._onDataArrival;
              }, set: function(e4) {
                this._onDataArrival = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "onError", { get: function() {
                return this._onError;
              }, set: function(e4) {
                this._onError = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "onComplete", { get: function() {
                return this._onComplete;
              }, set: function(e4) {
                this._onComplete = e4;
              }, enumerable: false, configurable: true }), e3.prototype.open = function(e4, t3) {
                throw new a.NotImplementedException("Unimplemented abstract function!");
              }, e3.prototype.abort = function() {
                throw new a.NotImplementedException("Unimplemented abstract function!");
              }, e3;
            })();
          }, 311: function(e2, t2, i2) {
            "use strict";
            i2.r(t2), i2.d(t2, { default: function() {
              return O;
            } });
            var a = i2(811), n = i2(653), r = { enableWorker: false, enableWorkerForMSE: false, enableStashBuffer: true, stashInitialSize: void 0, isLive: false, liveBufferLatencyChasing: false, liveBufferLatencyChasingOnPaused: false, liveBufferLatencyMaxLatency: 1.5, liveBufferLatencyMinRemain: 0.5, liveSync: false, liveSyncMaxLatency: 1.2, liveSyncTargetLatency: 0.8, liveSyncPlaybackRate: 1.2, lazyLoad: true, lazyLoadMaxDuration: 180, lazyLoadRecoverDuration: 30, deferLoadAfterSourceOpen: true, autoCleanupMaxBackwardDuration: 180, autoCleanupMinBackwardDuration: 120, statisticsInfoReportInterval: 600, fixAudioTimestampGap: true, accurateSeek: false, seekType: "range", seekParamStart: "bstart", seekParamEnd: "bend", rangeLoadZeroStart: false, customSeekHandler: void 0, reuseRedirectedURL: false, headers: void 0, customLoader: void 0 };
            function o() {
              return Object.assign({}, r);
            }
            var s = (function() {
              function e3() {
              }
              return e3.supportMSEH264Playback = function() {
                var e4 = 'video/mp4; codecs="avc1.42E01E,mp4a.40.2"', t3 = self.MediaSource && self.MediaSource.isTypeSupported(e4), i3 = self.ManagedMediaSource && self.ManagedMediaSource.isTypeSupported(e4);
                return t3 || i3;
              }, e3.supportMSEH265Playback = function() {
                var e4 = 'video/mp4; codecs="hvc1.1.6.L93.B0"', t3 = self.MediaSource && self.MediaSource.isTypeSupported(e4), i3 = self.ManagedMediaSource && self.ManagedMediaSource.isTypeSupported(e4);
                return t3 || i3;
              }, e3.supportNetworkStreamIO = function() {
                var e4 = new n.default({}, o()), t3 = e4.loaderType;
                return e4.destroy(), "fetch-stream-loader" == t3 || "xhr-moz-chunked-loader" == t3;
              }, e3.getNetworkLoaderTypeName = function() {
                var e4 = new n.default({}, o()), t3 = e4.loaderType;
                return e4.destroy(), t3;
              }, e3.supportNativeMediaPlayback = function(t3) {
                null == e3.videoElement && (e3.videoElement = window.document.createElement("video"));
                var i3 = e3.videoElement.canPlayType(t3);
                return "probably" === i3 || "maybe" == i3;
              }, e3.getFeatureList = function() {
                var t3 = { msePlayback: false, mseLivePlayback: false, mseH265Playback: false, networkStreamIO: false, networkLoaderName: "", nativeMP4H264Playback: false, nativeMP4H265Playback: false, nativeWebmVP8Playback: false, nativeWebmVP9Playback: false };
                return t3.msePlayback = e3.supportMSEH264Playback(), t3.networkStreamIO = e3.supportNetworkStreamIO(), t3.networkLoaderName = e3.getNetworkLoaderTypeName(), t3.mseLivePlayback = t3.msePlayback && t3.networkStreamIO, t3.mseH265Playback = e3.supportMSEH265Playback(), t3.nativeMP4H264Playback = e3.supportNativeMediaPlayback('video/mp4; codecs="avc1.42001E, mp4a.40.2"'), t3.nativeMP4H265Playback = e3.supportNativeMediaPlayback('video/mp4; codecs="hvc1.1.6.L93.B0"'), t3.nativeWebmVP8Playback = e3.supportNativeMediaPlayback('video/webm; codecs="vp8.0, vorbis"'), t3.nativeWebmVP9Playback = e3.supportNativeMediaPlayback('video/webm; codecs="vp9"'), t3;
              }, e3;
            })(), d = s, _ = i2(470), l = i2(856), u = i2(7), c = i2.n(u), h = i2(346), f = i2(117), p = i2(527), m = i2(403), g = i2(355), v = i2(867), y = i2(726), S = i2(994), E = i2(47), A = (function() {
              function e3(e4, t3, i3) {
                this.TAG = "SeekingHandler", this._config = null, this._media_element = null, this._always_seek_keyframe = false, this._on_unbuffered_seek = null, this._request_set_current_time = false, this._seek_request_record_clocktime = null, this._idr_sample_list = new E.IDRSampleList(), this.e = null, this._config = e4, this._media_element = t3, this._on_unbuffered_seek = i3, this.e = { onMediaSeeking: this._onMediaSeeking.bind(this) };
                var a2 = S.default.chrome && (S.default.version.major < 50 || 50 === S.default.version.major && S.default.version.build < 2661);
                this._always_seek_keyframe = !!(a2 || S.default.msedge || S.default.msie), this._always_seek_keyframe && (this._config.accurateSeek = false), this._media_element.addEventListener("seeking", this.e.onMediaSeeking);
              }
              return e3.prototype.destroy = function() {
                this._idr_sample_list.clear(), this._idr_sample_list = null, this._media_element.removeEventListener("seeking", this.e.onMediaSeeking), this._media_element = null, this._on_unbuffered_seek = null;
              }, e3.prototype.seek = function(e4) {
                var t3 = this._isPositionBuffered(e4), i3 = false;
                if (e4 < 1 && this._media_element.buffered.length > 0) {
                  var a2 = this._media_element.buffered.start(0);
                  (a2 < 1 && e4 < a2 || S.default.safari) && (i3 = true, e4 = S.default.safari ? 0.1 : a2);
                }
                if (i3) this.directSeek(e4);
                else if (t3) if (this._always_seek_keyframe) {
                  var n2 = this._getNearestKeyframe(Math.floor(1e3 * e4));
                  null != n2 && (e4 = n2.dts / 1e3), this.directSeek(e4);
                } else this.directSeek(e4);
                else this._idr_sample_list.clear(), this._on_unbuffered_seek(Math.floor(1e3 * e4)), this._config.accurateSeek && this.directSeek(e4);
              }, e3.prototype.directSeek = function(e4) {
                this._request_set_current_time = true, this._media_element.currentTime = e4;
              }, e3.prototype.appendSyncPoints = function(e4) {
                this._idr_sample_list.appendArray(e4);
              }, e3.prototype._onMediaSeeking = function(t3) {
                if (this._request_set_current_time) this._request_set_current_time = false;
                else {
                  var i3 = this._media_element.currentTime, a2 = this._media_element.buffered;
                  if (i3 < 1 && a2.length > 0) {
                    var n2 = a2.start(0);
                    if (n2 < 1 && i3 < n2 || S.default.safari) {
                      var r2 = S.default.safari ? 0.1 : n2;
                      return void this.directSeek(r2);
                    }
                  }
                  if (this._isPositionBuffered(i3)) {
                    if (this._always_seek_keyframe) {
                      var o2 = this._getNearestKeyframe(Math.floor(1e3 * i3));
                      null != o2 && (i3 = o2.dts / 1e3, this.directSeek(i3));
                    }
                  } else this._seek_request_record_clocktime = e3._getClockTime(), window.setTimeout(this._pollAndApplyUnbufferedSeek.bind(this), 50);
                }
              }, e3.prototype._pollAndApplyUnbufferedSeek = function() {
                if (null != this._seek_request_record_clocktime) if (this._seek_request_record_clocktime <= e3._getClockTime() - 100) {
                  var t3 = this._media_element.currentTime;
                  this._seek_request_record_clocktime = null, this._isPositionBuffered(t3) || (this._idr_sample_list.clear(), this._on_unbuffered_seek(Math.floor(1e3 * t3)), this._config.accurateSeek && this.directSeek(t3));
                } else window.setTimeout(this._pollAndApplyUnbufferedSeek.bind(this), 50);
              }, e3.prototype._isPositionBuffered = function(e4) {
                for (var t3 = this._media_element.buffered, i3 = 0; i3 < t3.length; i3++) {
                  var a2 = t3.start(i3), n2 = t3.end(i3);
                  if (e4 >= a2 && e4 < n2) return true;
                }
                return false;
              }, e3.prototype._getNearestKeyframe = function(e4) {
                return this._idr_sample_list.getLastSyncPointBeforeDts(e4);
              }, e3._getClockTime = function() {
                return self.performance && self.performance.now ? self.performance.now() : Date.now();
              }, e3;
            })(), b = (function() {
              function e3(e4, t3, i3, a2) {
                this.TAG = "LoadingController", this._config = null, this._media_element = null, this._on_pause_transmuxer = null, this._on_resume_transmuxer = null, this._paused = false, this.e = null, this._config = e4, this._media_element = t3, this._on_pause_transmuxer = i3, this._on_resume_transmuxer = a2, this.e = { onMediaTimeUpdate: this._onMediaTimeUpdate.bind(this) };
              }
              return e3.prototype.destroy = function() {
                this._media_element.removeEventListener("timeupdate", this.e.onMediaTimeUpdate), this.e = null, this._media_element = null, this._config = null, this._on_pause_transmuxer = null, this._on_resume_transmuxer = null;
              }, e3.prototype.notifyBufferedPositionChanged = function(e4) {
                !this._config.isLive && this._config.lazyLoad && (null == e4 ? this._suspendTransmuxerIfNeeded() : this._suspendTransmuxerIfBufferedPositionExceeded(e4));
              }, e3.prototype._onMediaTimeUpdate = function(e4) {
                this._paused && this._resumeTransmuxerIfNeeded();
              }, e3.prototype._suspendTransmuxerIfNeeded = function() {
                for (var e4 = this._media_element.buffered, t3 = this._media_element.currentTime, i3 = 0, a2 = 0; a2 < e4.length; a2++) {
                  var n2 = e4.start(a2), r2 = e4.end(a2);
                  if (n2 <= t3 && t3 < r2) {
                    i3 = r2;
                    break;
                  }
                }
                i3 > 0 && this._suspendTransmuxerIfBufferedPositionExceeded(i3);
              }, e3.prototype._suspendTransmuxerIfBufferedPositionExceeded = function(e4) {
                e4 >= this._media_element.currentTime + this._config.lazyLoadMaxDuration && !this._paused && (l.default.v(this.TAG, "Maximum buffering duration exceeded, suspend transmuxing task"), this.suspendTransmuxer(), this._media_element.addEventListener("timeupdate", this.e.onMediaTimeUpdate));
              }, e3.prototype.suspendTransmuxer = function() {
                this._paused = true, this._on_pause_transmuxer();
              }, e3.prototype._resumeTransmuxerIfNeeded = function() {
                for (var e4 = this._media_element.buffered, t3 = this._media_element.currentTime, i3 = this._config.lazyLoadRecoverDuration, a2 = false, n2 = 0; n2 < e4.length; n2++) {
                  var r2 = e4.start(n2), o2 = e4.end(n2);
                  if (t3 >= r2 && t3 < o2) {
                    t3 >= o2 - i3 && (a2 = true);
                    break;
                  }
                }
                a2 && (l.default.v(this.TAG, "Continue loading from paused position"), this.resumeTransmuxer(), this._media_element.removeEventListener("timeupdate", this.e.onMediaTimeUpdate));
              }, e3.prototype.resumeTransmuxer = function() {
                this._paused = false, this._on_resume_transmuxer();
              }, e3;
            })(), R = (function() {
              function e3(e4, t3) {
                this.TAG = "StartupStallJumper", this._media_element = null, this._on_direct_seek = null, this._canplay_received = false, this.e = null, this._media_element = e4, this._on_direct_seek = t3, this.e = { onMediaCanPlay: this._onMediaCanPlay.bind(this), onMediaStalled: this._onMediaStalled.bind(this), onMediaProgress: this._onMediaProgress.bind(this) }, this._media_element.addEventListener("canplay", this.e.onMediaCanPlay), this._media_element.addEventListener("stalled", this.e.onMediaStalled), this._media_element.addEventListener("progress", this.e.onMediaProgress);
              }
              return e3.prototype.destroy = function() {
                this._media_element.removeEventListener("canplay", this.e.onMediaCanPlay), this._media_element.removeEventListener("stalled", this.e.onMediaStalled), this._media_element.removeEventListener("progress", this.e.onMediaProgress), this._media_element = null, this._on_direct_seek = null;
              }, e3.prototype._onMediaCanPlay = function(e4) {
                this._canplay_received = true, this._media_element.removeEventListener("canplay", this.e.onMediaCanPlay);
              }, e3.prototype._onMediaStalled = function(e4) {
                this._detectAndFixStuckPlayback(true);
              }, e3.prototype._onMediaProgress = function(e4) {
                this._detectAndFixStuckPlayback();
              }, e3.prototype._detectAndFixStuckPlayback = function(e4) {
                var t3 = this._media_element, i3 = t3.buffered;
                e4 || !this._canplay_received || t3.readyState < 2 ? i3.length > 0 && t3.currentTime < i3.start(0) && (l.default.w(this.TAG, "Playback seems stuck at ".concat(t3.currentTime, ", seek to ").concat(i3.start(0))), this._on_direct_seek(i3.start(0)), this._media_element.removeEventListener("progress", this.e.onMediaProgress)) : this._media_element.removeEventListener("progress", this.e.onMediaProgress);
              }, e3;
            })(), T = (function() {
              function e3(e4, t3, i3) {
                this._config = null, this._media_element = null, this._on_direct_seek = null, this._config = e4, this._media_element = t3, this._on_direct_seek = i3;
              }
              return e3.prototype.destroy = function() {
                this._on_direct_seek = null, this._media_element = null, this._config = null;
              }, e3.prototype.notifyBufferedRangeUpdate = function() {
                this._chaseLiveLatency();
              }, e3.prototype._chaseLiveLatency = function() {
                var e4 = this._media_element.buffered, t3 = this._media_element.currentTime, i3 = this._media_element.paused;
                if (this._config.isLive && this._config.liveBufferLatencyChasing && 0 != e4.length && (this._config.liveBufferLatencyChasingOnPaused || !i3)) {
                  var a2 = e4.end(e4.length - 1);
                  if (a2 > this._config.liveBufferLatencyMaxLatency && a2 - t3 > this._config.liveBufferLatencyMaxLatency) {
                    var n2 = a2 - this._config.liveBufferLatencyMinRemain;
                    this._on_direct_seek(n2);
                  }
                }
              }, e3;
            })(), L = (function() {
              function e3(e4, t3) {
                this._config = null, this._media_element = null, this.e = null, this._config = e4, this._media_element = t3, this.e = { onMediaTimeUpdate: this._onMediaTimeUpdate.bind(this) }, this._media_element.addEventListener("timeupdate", this.e.onMediaTimeUpdate);
              }
              return e3.prototype.destroy = function() {
                this._media_element.removeEventListener("timeupdate", this.e.onMediaTimeUpdate), this._media_element = null, this._config = null;
              }, e3.prototype._onMediaTimeUpdate = function(e4) {
                if (this._config.isLive && this._config.liveSync) {
                  var t3 = this._getCurrentLatency();
                  if (t3 > this._config.liveSyncMaxLatency) {
                    var i3 = Math.min(2, Math.max(1, this._config.liveSyncPlaybackRate));
                    this._media_element.playbackRate = i3;
                  } else t3 > this._config.liveSyncTargetLatency || 1 !== this._media_element.playbackRate && 0 !== this._media_element.playbackRate && (this._media_element.playbackRate = 1);
                }
              }, e3.prototype._getCurrentLatency = function() {
                if (!this._media_element) return 0;
                var e4 = this._media_element.buffered, t3 = this._media_element.currentTime;
                return 0 == e4.length ? 0 : e4.end(e4.length - 1) - t3;
              }, e3;
            })(), k = (function() {
              function e3(e4, t3) {
                this.TAG = "PlayerEngineMainThread", this._emitter = new u(), this._media_element = null, this._mse_controller = null, this._transmuxer = null, this._pending_seek_time = null, this._seeking_handler = null, this._loading_controller = null, this._startup_stall_jumper = null, this._live_latency_chaser = null, this._live_latency_synchronizer = null, this._mse_source_opened = false, this._has_pending_load = false, this._loaded_metadata_received = false, this._media_info = null, this._statistics_info = null, this.e = null, this._media_data_source = e4, this._config = o(), "object" == typeof t3 && Object.assign(this._config, t3), true === e4.isLive && (this._config.isLive = true), this.e = { onMediaLoadedMetadata: this._onMediaLoadedMetadata.bind(this) };
              }
              return e3.prototype.destroy = function() {
                this._emitter.emit(f.default.DESTROYING), this._transmuxer && this.unload(), this._media_element && this.detachMediaElement(), this.e = null, this._media_data_source = null, this._emitter.removeAllListeners(), this._emitter = null;
              }, e3.prototype.on = function(e4, t3) {
                var i3 = this;
                this._emitter.addListener(e4, t3), e4 === f.default.MEDIA_INFO && this._media_info ? Promise.resolve().then(function() {
                  return i3._emitter.emit(f.default.MEDIA_INFO, i3.mediaInfo);
                }) : e4 == f.default.STATISTICS_INFO && this._statistics_info && Promise.resolve().then(function() {
                  return i3._emitter.emit(f.default.STATISTICS_INFO, i3.statisticsInfo);
                });
              }, e3.prototype.off = function(e4, t3) {
                this._emitter.removeListener(e4, t3);
              }, e3.prototype.attachMediaElement = function(e4) {
                var t3 = this;
                this._media_element = e4, e4.src = "", e4.removeAttribute("src"), e4.srcObject = null, e4.load(), e4.addEventListener("loadedmetadata", this.e.onMediaLoadedMetadata), this._mse_controller = new h.default(this._config), this._mse_controller.on(m.default.UPDATE_END, this._onMSEUpdateEnd.bind(this)), this._mse_controller.on(m.default.BUFFER_FULL, this._onMSEBufferFull.bind(this)), this._mse_controller.on(m.default.SOURCE_OPEN, this._onMSESourceOpen.bind(this)), this._mse_controller.on(m.default.ERROR, this._onMSEError.bind(this)), this._mse_controller.on(m.default.START_STREAMING, this._onMSEStartStreaming.bind(this)), this._mse_controller.on(m.default.END_STREAMING, this._onMSEEndStreaming.bind(this)), this._mse_controller.initialize({ getCurrentTime: function() {
                  return t3._media_element.currentTime;
                }, getReadyState: function() {
                  return t3._media_element.readyState;
                } }), this._mse_controller.isManagedMediaSource() ? (e4.disableRemotePlayback = true, e4.srcObject = this._mse_controller.getObject()) : e4.src = this._mse_controller.getObjectURL();
              }, e3.prototype.detachMediaElement = function() {
                this._media_element && (this._mse_controller.shutdown(), this._media_element.removeEventListener("loadedmetadata", this.e.onMediaLoadedMetadata), this._media_element.src = "", this._media_element.removeAttribute("src"), this._media_element.srcObject = null, this._media_element.load(), this._media_element = null, this._mse_controller.revokeObjectURL()), this._mse_controller && (this._mse_controller.destroy(), this._mse_controller = null);
              }, e3.prototype.load = function() {
                var e4 = this;
                if (!this._media_element) throw new v.IllegalStateException("HTMLMediaElement must be attached before load()!");
                if (this._transmuxer) throw new v.IllegalStateException("load() has been called, please call unload() first!");
                this._has_pending_load || (!this._config.deferLoadAfterSourceOpen || this._mse_source_opened ? (this._transmuxer = new p.default(this._media_data_source, this._config), this._transmuxer.on(y.default.INIT_SEGMENT, function(t3, i3) {
                  e4._mse_controller.appendInitSegment(i3);
                }), this._transmuxer.on(y.default.MEDIA_SEGMENT, function(t3, i3) {
                  e4._mse_controller.appendMediaSegment(i3), !e4._config.isLive && "video" === t3 && i3.data && i3.data.byteLength > 0 && "info" in i3 && e4._seeking_handler.appendSyncPoints(i3.info.syncPoints), e4._loading_controller.notifyBufferedPositionChanged(i3.info.endDts / 1e3);
                }), this._transmuxer.on(y.default.LOADING_COMPLETE, function() {
                  e4._mse_controller.endOfStream(), e4._emitter.emit(f.default.LOADING_COMPLETE);
                }), this._transmuxer.on(y.default.RECOVERED_EARLY_EOF, function() {
                  e4._emitter.emit(f.default.RECOVERED_EARLY_EOF);
                }), this._transmuxer.on(y.default.IO_ERROR, function(t3, i3) {
                  e4._emitter.emit(f.default.ERROR, g.ErrorTypes.NETWORK_ERROR, t3, i3);
                }), this._transmuxer.on(y.default.DEMUX_ERROR, function(t3, i3) {
                  e4._emitter.emit(f.default.ERROR, g.ErrorTypes.MEDIA_ERROR, t3, i3);
                }), this._transmuxer.on(y.default.MEDIA_INFO, function(t3) {
                  e4._media_info = t3, e4._emitter.emit(f.default.MEDIA_INFO, Object.assign({}, t3));
                }), this._transmuxer.on(y.default.STATISTICS_INFO, function(t3) {
                  e4._statistics_info = e4._fillStatisticsInfo(t3), e4._emitter.emit(f.default.STATISTICS_INFO, Object.assign({}, t3));
                }), this._transmuxer.on(y.default.RECOMMEND_SEEKPOINT, function(t3) {
                  e4._media_element && !e4._config.accurateSeek && e4._seeking_handler.directSeek(t3 / 1e3);
                }), this._transmuxer.on(y.default.METADATA_ARRIVED, function(t3) {
                  e4._emitter.emit(f.default.METADATA_ARRIVED, t3);
                }), this._transmuxer.on(y.default.SCRIPTDATA_ARRIVED, function(t3) {
                  e4._emitter.emit(f.default.SCRIPTDATA_ARRIVED, t3);
                }), this._transmuxer.on(y.default.TIMED_ID3_METADATA_ARRIVED, function(t3) {
                  e4._emitter.emit(f.default.TIMED_ID3_METADATA_ARRIVED, t3);
                }), this._transmuxer.on(y.default.PGS_SUBTITLE_ARRIVED, function(t3) {
                  e4._emitter.emit(f.default.PGS_SUBTITLE_ARRIVED, t3);
                }), this._transmuxer.on(y.default.SYNCHRONOUS_KLV_METADATA_ARRIVED, function(t3) {
                  e4._emitter.emit(f.default.SYNCHRONOUS_KLV_METADATA_ARRIVED, t3);
                }), this._transmuxer.on(y.default.ASYNCHRONOUS_KLV_METADATA_ARRIVED, function(t3) {
                  e4._emitter.emit(f.default.ASYNCHRONOUS_KLV_METADATA_ARRIVED, t3);
                }), this._transmuxer.on(y.default.SMPTE2038_METADATA_ARRIVED, function(t3) {
                  e4._emitter.emit(f.default.SMPTE2038_METADATA_ARRIVED, t3);
                }), this._transmuxer.on(y.default.SEI_ARRIVED, function(t3) {
                  e4._emitter.emit(f.default.SEI_ARRIVED, t3);
                }), this._transmuxer.on(y.default.SCTE35_METADATA_ARRIVED, function(t3) {
                  e4._emitter.emit(f.default.SCTE35_METADATA_ARRIVED, t3);
                }), this._transmuxer.on(y.default.PES_PRIVATE_DATA_DESCRIPTOR, function(t3) {
                  e4._emitter.emit(f.default.PES_PRIVATE_DATA_DESCRIPTOR, t3);
                }), this._transmuxer.on(y.default.PES_PRIVATE_DATA_ARRIVED, function(t3) {
                  e4._emitter.emit(f.default.PES_PRIVATE_DATA_ARRIVED, t3);
                }), this._seeking_handler = new A(this._config, this._media_element, this._onRequiredUnbufferedSeek.bind(this)), this._loading_controller = new b(this._config, this._media_element, this._onRequestPauseTransmuxer.bind(this), this._onRequestResumeTransmuxer.bind(this)), this._startup_stall_jumper = new R(this._media_element, this._onRequestDirectSeek.bind(this)), this._config.isLive && this._config.liveBufferLatencyChasing && (this._live_latency_chaser = new T(this._config, this._media_element, this._onRequestDirectSeek.bind(this))), this._config.isLive && this._config.liveSync && (this._live_latency_synchronizer = new L(this._config, this._media_element)), this._media_element.readyState > 0 && this._seeking_handler.directSeek(0), this._transmuxer.open()) : this._has_pending_load = true);
              }, e3.prototype.unload = function() {
                var e4, t3, i3, a2, n2, r2, o2, s2, d2;
                null === (e4 = this._media_element) || void 0 === e4 || e4.pause(), null === (t3 = this._live_latency_synchronizer) || void 0 === t3 || t3.destroy(), this._live_latency_synchronizer = null, null === (i3 = this._live_latency_chaser) || void 0 === i3 || i3.destroy(), this._live_latency_chaser = null, null === (a2 = this._startup_stall_jumper) || void 0 === a2 || a2.destroy(), this._startup_stall_jumper = null, null === (n2 = this._loading_controller) || void 0 === n2 || n2.destroy(), this._loading_controller = null, null === (r2 = this._seeking_handler) || void 0 === r2 || r2.destroy(), this._seeking_handler = null, null === (o2 = this._mse_controller) || void 0 === o2 || o2.flush(), null === (s2 = this._transmuxer) || void 0 === s2 || s2.close(), null === (d2 = this._transmuxer) || void 0 === d2 || d2.destroy(), this._transmuxer = null;
              }, e3.prototype.play = function() {
                return this._media_element.play();
              }, e3.prototype.pause = function() {
                this._media_element.pause();
              }, e3.prototype.seek = function(e4) {
                this._media_element && this._seeking_handler ? this._seeking_handler.seek(e4) : this._pending_seek_time = e4;
              }, Object.defineProperty(e3.prototype, "mediaInfo", { get: function() {
                return Object.assign({}, this._media_info);
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "statisticsInfo", { get: function() {
                return Object.assign({}, this._statistics_info);
              }, enumerable: false, configurable: true }), e3.prototype._onMSESourceOpen = function() {
                this._mse_source_opened = true, this._has_pending_load && (this._has_pending_load = false, this.load());
              }, e3.prototype._onMSEUpdateEnd = function() {
                this._config.isLive && this._config.liveBufferLatencyChasing && this._live_latency_chaser && this._live_latency_chaser.notifyBufferedRangeUpdate(), this._loading_controller.notifyBufferedPositionChanged();
              }, e3.prototype._onMSEBufferFull = function() {
                l.default.v(this.TAG, "MSE SourceBuffer is full, suspend transmuxing task"), this._loading_controller.suspendTransmuxer();
              }, e3.prototype._onMSEError = function(e4) {
                this._emitter.emit(f.default.ERROR, g.ErrorTypes.MEDIA_ERROR, g.ErrorDetails.MEDIA_MSE_ERROR, e4);
              }, e3.prototype._onMSEStartStreaming = function() {
                this._loaded_metadata_received && (this._config.isLive || (l.default.v(this.TAG, "Resume transmuxing task due to ManagedMediaSource onStartStreaming"), this._loading_controller.resumeTransmuxer()));
              }, e3.prototype._onMSEEndStreaming = function() {
                this._config.isLive || (l.default.v(this.TAG, "Suspend transmuxing task due to ManagedMediaSource onEndStreaming"), this._loading_controller.suspendTransmuxer());
              }, e3.prototype._onMediaLoadedMetadata = function(e4) {
                this._loaded_metadata_received = true, null != this._pending_seek_time && (this._seeking_handler.seek(this._pending_seek_time), this._pending_seek_time = null);
              }, e3.prototype._onRequestDirectSeek = function(e4) {
                this._seeking_handler.directSeek(e4);
              }, e3.prototype._onRequiredUnbufferedSeek = function(e4) {
                this._mse_controller.flush(), this._transmuxer.seek(e4);
              }, e3.prototype._onRequestPauseTransmuxer = function() {
                this._transmuxer.pause();
              }, e3.prototype._onRequestResumeTransmuxer = function() {
                this._transmuxer.resume();
              }, e3.prototype._fillStatisticsInfo = function(e4) {
                if (e4.playerType = "MSEPlayer", !(this._media_element instanceof HTMLVideoElement)) return e4;
                var t3 = true, i3 = 0, a2 = 0;
                if (this._media_element.getVideoPlaybackQuality) {
                  var n2 = this._media_element.getVideoPlaybackQuality();
                  i3 = n2.totalVideoFrames, a2 = n2.droppedVideoFrames;
                } else null != this._media_element.webkitDecodedFrameCount ? (i3 = this._media_element.webkitDecodedFrameCount, a2 = this._media_element.webkitDroppedFrameCount) : t3 = false;
                return t3 && (e4.decodedFrames = i3, e4.droppedFrames = a2), e4;
              }, e3;
            })(), D = i2(861), M = i2(947), C = (function() {
              function e3(e4, t3) {
                this.TAG = "PlayerEngineDedicatedThread", this._emitter = new u(), this._media_element = null, this._worker_destroying = false, this._seeking_handler = null, this._loading_controller = null, this._startup_stall_jumper = null, this._live_latency_chaser = null, this._live_latency_synchronizer = null, this._pending_seek_time = null, this._media_info = null, this._statistics_info = null, this.e = null, this._media_data_source = e4, this._config = o(), "object" == typeof t3 && Object.assign(this._config, t3), true === e4.isLive && (this._config.isLive = true), this.e = { onLoggingConfigChanged: this._onLoggingConfigChanged.bind(this), onMediaLoadedMetadata: this._onMediaLoadedMetadata.bind(this), onMediaTimeUpdate: this._onMediaTimeUpdate.bind(this), onMediaReadyStateChanged: this._onMediaReadyStateChange.bind(this) }, M.default.registerListener(this.e.onLoggingConfigChanged), this._worker = D(877, { all: true }), this._worker.addEventListener("message", this._onWorkerMessage.bind(this)), this._worker.postMessage({ cmd: "init", media_data_source: this._media_data_source, config: this._config }), this._worker.postMessage({ cmd: "logging_config", logging_config: M.default.getConfig() });
              }
              return e3.isSupported = function() {
                return !!(self.Worker && (self.MediaSource && "canConstructInDedicatedWorker" in self.MediaSource && true === self.MediaSource.canConstructInDedicatedWorker || self.ManagedMediaSource && "canConstructInDedicatedWorker" in self.ManagedMediaSource && true === self.ManagedMediaSource.canConstructInDedicatedWorker));
              }, e3.prototype.destroy = function() {
                this._emitter.emit(f.default.DESTROYING), this.unload(), this.detachMediaElement(), this._worker_destroying = true, this._worker.postMessage({ cmd: "destroy" }), M.default.removeListener(this.e.onLoggingConfigChanged), this.e = null, this._media_data_source = null, this._emitter.removeAllListeners(), this._emitter = null;
              }, e3.prototype.on = function(e4, t3) {
                var i3 = this;
                this._emitter.addListener(e4, t3), e4 === f.default.MEDIA_INFO && this._media_info ? Promise.resolve().then(function() {
                  return i3._emitter.emit(f.default.MEDIA_INFO, i3.mediaInfo);
                }) : e4 == f.default.STATISTICS_INFO && this._statistics_info && Promise.resolve().then(function() {
                  return i3._emitter.emit(f.default.STATISTICS_INFO, i3.statisticsInfo);
                });
              }, e3.prototype.off = function(e4, t3) {
                this._emitter.removeListener(e4, t3);
              }, e3.prototype.attachMediaElement = function(e4) {
                this._media_element = e4, this._media_element.src = "", this._media_element.removeAttribute("src"), this._media_element.srcObject = null, this._media_element.load(), this._media_element.addEventListener("loadedmetadata", this.e.onMediaLoadedMetadata), this._media_element.addEventListener("timeupdate", this.e.onMediaTimeUpdate), this._media_element.addEventListener("readystatechange", this.e.onMediaReadyStateChanged), this._worker.postMessage({ cmd: "initialize_mse" });
              }, e3.prototype.detachMediaElement = function() {
                this._worker.postMessage({ cmd: "shutdown_mse" }), this._media_element && (this._media_element.removeEventListener("loadedmetadata", this.e.onMediaLoadedMetadata), this._media_element.removeEventListener("timeupdate", this.e.onMediaTimeUpdate), this._media_element.removeEventListener("readystatechange", this.e.onMediaReadyStateChanged), this._media_element.src = "", this._media_element.removeAttribute("src"), this._media_element.srcObject = null, this._media_element.load(), this._media_element = null);
              }, e3.prototype.load = function() {
                this._worker.postMessage({ cmd: "load" }), this._seeking_handler = new A(this._config, this._media_element, this._onRequiredUnbufferedSeek.bind(this)), this._loading_controller = new b(this._config, this._media_element, this._onRequestPauseTransmuxer.bind(this), this._onRequestResumeTransmuxer.bind(this)), this._startup_stall_jumper = new R(this._media_element, this._onRequestDirectSeek.bind(this)), this._config.isLive && this._config.liveBufferLatencyChasing && (this._live_latency_chaser = new T(this._config, this._media_element, this._onRequestDirectSeek.bind(this))), this._config.isLive && this._config.liveSync && (this._live_latency_synchronizer = new L(this._config, this._media_element)), this._media_element.readyState > 0 && this._seeking_handler.directSeek(0);
              }, e3.prototype.unload = function() {
                var e4, t3, i3, a2, n2, r2;
                null === (e4 = this._media_element) || void 0 === e4 || e4.pause(), this._worker.postMessage({ cmd: "unload" }), null === (t3 = this._live_latency_synchronizer) || void 0 === t3 || t3.destroy(), this._live_latency_synchronizer = null, null === (i3 = this._live_latency_chaser) || void 0 === i3 || i3.destroy(), this._live_latency_chaser = null, null === (a2 = this._startup_stall_jumper) || void 0 === a2 || a2.destroy(), this._startup_stall_jumper = null, null === (n2 = this._loading_controller) || void 0 === n2 || n2.destroy(), this._loading_controller = null, null === (r2 = this._seeking_handler) || void 0 === r2 || r2.destroy(), this._seeking_handler = null;
              }, e3.prototype.play = function() {
                return this._media_element.play();
              }, e3.prototype.pause = function() {
                this._media_element.pause();
              }, e3.prototype.seek = function(e4) {
                this._media_element && this._seeking_handler ? this._seeking_handler.seek(e4) : this._pending_seek_time = e4;
              }, Object.defineProperty(e3.prototype, "mediaInfo", { get: function() {
                return Object.assign({}, this._media_info);
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "statisticsInfo", { get: function() {
                return Object.assign({}, this._statistics_info);
              }, enumerable: false, configurable: true }), e3.prototype._onLoggingConfigChanged = function(e4) {
                var t3;
                null === (t3 = this._worker) || void 0 === t3 || t3.postMessage({ cmd: "logging_config", logging_config: e4 });
              }, e3.prototype._onMSEUpdateEnd = function() {
                this._config.isLive && this._config.liveBufferLatencyChasing && this._live_latency_chaser && this._live_latency_chaser.notifyBufferedRangeUpdate(), this._loading_controller.notifyBufferedPositionChanged();
              }, e3.prototype._onMSEBufferFull = function() {
                l.default.v(this.TAG, "MSE SourceBuffer is full, suspend transmuxing task"), this._loading_controller.suspendTransmuxer();
              }, e3.prototype._onMediaLoadedMetadata = function(e4) {
                null != this._pending_seek_time && (this._seeking_handler.seek(this._pending_seek_time), this._pending_seek_time = null);
              }, e3.prototype._onRequestDirectSeek = function(e4) {
                this._seeking_handler.directSeek(e4);
              }, e3.prototype._onRequiredUnbufferedSeek = function(e4) {
                this._worker.postMessage({ cmd: "unbuffered_seek", milliseconds: e4 });
              }, e3.prototype._onRequestPauseTransmuxer = function() {
                this._worker.postMessage({ cmd: "pause_transmuxer" });
              }, e3.prototype._onRequestResumeTransmuxer = function() {
                this._worker.postMessage({ cmd: "resume_transmuxer" });
              }, e3.prototype._onMediaTimeUpdate = function(e4) {
                this._worker.postMessage({ cmd: "timeupdate", current_time: e4.target.currentTime });
              }, e3.prototype._onMediaReadyStateChange = function(e4) {
                this._worker.postMessage({ cmd: "readystatechange", ready_state: e4.target.readyState });
              }, e3.prototype._onWorkerMessage = function(e4) {
                var t3, i3 = e4.data, a2 = i3.msg;
                if ("destroyed" == a2 || this._worker_destroying) return this._worker_destroying = false, null === (t3 = this._worker) || void 0 === t3 || t3.terminate(), void (this._worker = null);
                switch (a2) {
                  case "mse_init":
                    var n2 = i3;
                    "function" == typeof self.ManagedMediaSource && !("function" == typeof self.MediaSource) && (this._media_element.disableRemotePlayback = true), this._media_element.srcObject = n2.handle;
                    break;
                  case "mse_event":
                    (n2 = i3).event == m.default.UPDATE_END ? this._onMSEUpdateEnd() : n2.event == m.default.BUFFER_FULL && this._onMSEBufferFull();
                    break;
                  case "transmuxing_event":
                    if ((n2 = i3).event == y.default.MEDIA_INFO) {
                      var r2 = i3;
                      this._media_info = r2.info, this._emitter.emit(f.default.MEDIA_INFO, Object.assign({}, r2.info));
                    } else if (n2.event == y.default.STATISTICS_INFO) {
                      var o2 = i3;
                      this._statistics_info = this._fillStatisticsInfo(o2.info), this._emitter.emit(f.default.STATISTICS_INFO, Object.assign({}, o2.info));
                    } else if (n2.event == y.default.RECOMMEND_SEEKPOINT) {
                      var s2 = i3;
                      this._media_element && !this._config.accurateSeek && this._seeking_handler.directSeek(s2.milliseconds / 1e3);
                    }
                    break;
                  case "player_event":
                    if ((n2 = i3).event == f.default.ERROR) {
                      var d2 = i3;
                      this._emitter.emit(f.default.ERROR, d2.error_type, d2.error_detail, d2.info);
                    } else if ("extraData" in n2) {
                      var _2 = i3;
                      this._emitter.emit(_2.event, _2.extraData);
                    }
                    break;
                  case "logcat_callback":
                    n2 = i3, l.default.emitter.emit("log", n2.type, n2.logcat);
                    break;
                  case "buffered_position_changed":
                    n2 = i3, this._loading_controller.notifyBufferedPositionChanged(n2.buffered_position_milliseconds / 1e3);
                }
              }, e3.prototype._fillStatisticsInfo = function(e4) {
                if (e4.playerType = "MSEPlayer", !(this._media_element instanceof HTMLVideoElement)) return e4;
                var t3 = true, i3 = 0, a2 = 0;
                if (this._media_element.getVideoPlaybackQuality) {
                  var n2 = this._media_element.getVideoPlaybackQuality();
                  i3 = n2.totalVideoFrames, a2 = n2.droppedVideoFrames;
                } else null != this._media_element.webkitDecodedFrameCount ? (i3 = this._media_element.webkitDecodedFrameCount, a2 = this._media_element.webkitDroppedFrameCount) : t3 = false;
                return t3 && (e4.decodedFrames = i3, e4.droppedFrames = a2), e4;
              }, e3;
            })(), w = (function() {
              function e3(e4, t3) {
                this.TAG = "MSEPlayer", this._type = "MSEPlayer", this._media_element = null, this._player_engine = null;
                var i3 = e4.type.toLowerCase();
                if ("mse" !== i3 && "mpegts" !== i3 && "m2ts" !== i3 && "flv" !== i3) throw new v.InvalidArgumentException("MSEPlayer requires an mpegts/m2ts/flv MediaDataSource input!");
                if (t3 && t3.enableWorkerForMSE && C.isSupported()) try {
                  this._player_engine = new C(e4, t3);
                } catch (i4) {
                  l.default.e(this.TAG, "Error while initializing PlayerEngineDedicatedThread, fallback to PlayerEngineMainThread"), this._player_engine = new k(e4, t3);
                }
                else this._player_engine = new k(e4, t3);
              }
              return e3.prototype.destroy = function() {
                this._player_engine.destroy(), this._player_engine = null, this._media_element = null;
              }, e3.prototype.on = function(e4, t3) {
                this._player_engine.on(e4, t3);
              }, e3.prototype.off = function(e4, t3) {
                this._player_engine.off(e4, t3);
              }, e3.prototype.attachMediaElement = function(e4) {
                this._media_element = e4, this._player_engine.attachMediaElement(e4);
              }, e3.prototype.detachMediaElement = function() {
                this._media_element = null, this._player_engine.detachMediaElement();
              }, e3.prototype.load = function() {
                this._player_engine.load();
              }, e3.prototype.unload = function() {
                this._player_engine.unload();
              }, e3.prototype.play = function() {
                return this._player_engine.play();
              }, e3.prototype.pause = function() {
                this._player_engine.pause();
              }, Object.defineProperty(e3.prototype, "type", { get: function() {
                return this._type;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "buffered", { get: function() {
                return this._media_element.buffered;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "duration", { get: function() {
                return this._media_element.duration;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "volume", { get: function() {
                return this._media_element.volume;
              }, set: function(e4) {
                this._media_element.volume = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "muted", { get: function() {
                return this._media_element.muted;
              }, set: function(e4) {
                this._media_element.muted = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "currentTime", { get: function() {
                return this._media_element ? this._media_element.currentTime : 0;
              }, set: function(e4) {
                this._player_engine.seek(e4);
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "mediaInfo", { get: function() {
                return this._player_engine.mediaInfo;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "statisticsInfo", { get: function() {
                return this._player_engine.statisticsInfo;
              }, enumerable: false, configurable: true }), e3;
            })(), I = (function() {
              function e3(e4, t3) {
                this.TAG = "NativePlayer", this._type = "NativePlayer", this._emitter = new (c())(), this._config = o(), "object" == typeof t3 && Object.assign(this._config, t3);
                var i3 = e4.type.toLowerCase();
                if ("mse" === i3 || "mpegts" === i3 || "m2ts" === i3 || "flv" === i3) throw new v.InvalidArgumentException("NativePlayer does't support mse/mpegts/m2ts/flv MediaDataSource input!");
                if (e4.hasOwnProperty("segments")) throw new v.InvalidArgumentException("NativePlayer(".concat(e4.type, ") doesn't support multipart playback!"));
                this.e = { onvLoadedMetadata: this._onvLoadedMetadata.bind(this) }, this._pendingSeekTime = null, this._statisticsReporter = null, this._mediaDataSource = e4, this._mediaElement = null;
              }
              return e3.prototype.destroy = function() {
                this._emitter.emit(f.default.DESTROYING), this._mediaElement && (this.unload(), this.detachMediaElement()), this.e = null, this._mediaDataSource = null, this._emitter.removeAllListeners(), this._emitter = null;
              }, e3.prototype.on = function(e4, t3) {
                var i3 = this;
                e4 === f.default.MEDIA_INFO ? null != this._mediaElement && 0 !== this._mediaElement.readyState && Promise.resolve().then(function() {
                  i3._emitter.emit(f.default.MEDIA_INFO, i3.mediaInfo);
                }) : e4 === f.default.STATISTICS_INFO && null != this._mediaElement && 0 !== this._mediaElement.readyState && Promise.resolve().then(function() {
                  i3._emitter.emit(f.default.STATISTICS_INFO, i3.statisticsInfo);
                }), this._emitter.addListener(e4, t3);
              }, e3.prototype.off = function(e4, t3) {
                this._emitter.removeListener(e4, t3);
              }, e3.prototype.attachMediaElement = function(e4) {
                if (this._mediaElement = e4, e4.addEventListener("loadedmetadata", this.e.onvLoadedMetadata), null != this._pendingSeekTime) try {
                  e4.currentTime = this._pendingSeekTime, this._pendingSeekTime = null;
                } catch (e5) {
                }
              }, e3.prototype.detachMediaElement = function() {
                this._mediaElement && (this._mediaElement.src = "", this._mediaElement.removeAttribute("src"), this._mediaElement.removeEventListener("loadedmetadata", this.e.onvLoadedMetadata), this._mediaElement = null), null != this._statisticsReporter && (window.clearInterval(this._statisticsReporter), this._statisticsReporter = null);
              }, e3.prototype.load = function() {
                if (!this._mediaElement) throw new v.IllegalStateException("HTMLMediaElement must be attached before load()!");
                this._mediaElement.src = this._mediaDataSource.url, this._mediaElement.readyState > 0 && (this._mediaElement.currentTime = 0), this._mediaElement.preload = "auto", this._mediaElement.load(), this._statisticsReporter = window.setInterval(this._reportStatisticsInfo.bind(this), this._config.statisticsInfoReportInterval);
              }, e3.prototype.unload = function() {
                this._mediaElement && (this._mediaElement.src = "", this._mediaElement.removeAttribute("src")), null != this._statisticsReporter && (window.clearInterval(this._statisticsReporter), this._statisticsReporter = null);
              }, e3.prototype.play = function() {
                return this._mediaElement.play();
              }, e3.prototype.pause = function() {
                this._mediaElement.pause();
              }, Object.defineProperty(e3.prototype, "type", { get: function() {
                return this._type;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "buffered", { get: function() {
                return this._mediaElement.buffered;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "duration", { get: function() {
                return this._mediaElement.duration;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "volume", { get: function() {
                return this._mediaElement.volume;
              }, set: function(e4) {
                this._mediaElement.volume = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "muted", { get: function() {
                return this._mediaElement.muted;
              }, set: function(e4) {
                this._mediaElement.muted = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "currentTime", { get: function() {
                return this._mediaElement ? this._mediaElement.currentTime : 0;
              }, set: function(e4) {
                this._mediaElement ? this._mediaElement.currentTime = e4 : this._pendingSeekTime = e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "mediaInfo", { get: function() {
                var e4 = { mimeType: (this._mediaElement instanceof HTMLAudioElement ? "audio/" : "video/") + this._mediaDataSource.type };
                return this._mediaElement && (e4.duration = Math.floor(1e3 * this._mediaElement.duration), this._mediaElement instanceof HTMLVideoElement && (e4.width = this._mediaElement.videoWidth, e4.height = this._mediaElement.videoHeight)), e4;
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "statisticsInfo", { get: function() {
                var e4 = { playerType: this._type, url: this._mediaDataSource.url };
                if (!(this._mediaElement instanceof HTMLVideoElement)) return e4;
                var t3 = true, i3 = 0, a2 = 0;
                if (this._mediaElement.getVideoPlaybackQuality) {
                  var n2 = this._mediaElement.getVideoPlaybackQuality();
                  i3 = n2.totalVideoFrames, a2 = n2.droppedVideoFrames;
                } else null != this._mediaElement.webkitDecodedFrameCount ? (i3 = this._mediaElement.webkitDecodedFrameCount, a2 = this._mediaElement.webkitDroppedFrameCount) : t3 = false;
                return t3 && (e4.decodedFrames = i3, e4.droppedFrames = a2), e4;
              }, enumerable: false, configurable: true }), e3.prototype._onvLoadedMetadata = function(e4) {
                null != this._pendingSeekTime && (this._mediaElement.currentTime = this._pendingSeekTime, this._pendingSeekTime = null), this._emitter.emit(f.default.MEDIA_INFO, this.mediaInfo);
              }, e3.prototype._reportStatisticsInfo = function() {
                this._emitter.emit(f.default.STATISTICS_INFO, this.statisticsInfo);
              }, e3;
            })();
            a.default.install();
            var B = { createPlayer: function(e3, t3) {
              var i3 = e3;
              if (null == i3 || "object" != typeof i3) throw new v.InvalidArgumentException("MediaDataSource must be an javascript object!");
              if (!i3.hasOwnProperty("type")) throw new v.InvalidArgumentException("MediaDataSource must has type field to indicate video file type!");
              switch (i3.type) {
                case "mse":
                case "mpegts":
                case "m2ts":
                case "flv":
                  return new w(i3, t3);
                default:
                  return new I(i3, t3);
              }
            }, isSupported: function() {
              return d.supportMSEH264Playback();
            }, getFeatureList: function() {
              return d.getFeatureList();
            } };
            B.BaseLoader = _.BaseLoader, B.LoaderStatus = _.LoaderStatus, B.LoaderErrors = _.LoaderErrors, B.Events = f.default, B.ErrorTypes = g.ErrorTypes, B.ErrorDetails = g.ErrorDetails, B.MSEPlayer = w, B.NativePlayer = I, B.LoggingControl = M.default, Object.defineProperty(B, "version", { enumerable: true, get: function() {
              return "1.8.2";
            } });
            var O = B;
          }, 355: function(e2, t2, i2) {
            "use strict";
            i2.r(t2), i2.d(t2, { ErrorDetails: function() {
              return o;
            }, ErrorTypes: function() {
              return r;
            } });
            var a = i2(470), n = i2(827), r = { NETWORK_ERROR: "NetworkError", MEDIA_ERROR: "MediaError", OTHER_ERROR: "OtherError" }, o = { NETWORK_EXCEPTION: a.LoaderErrors.EXCEPTION, NETWORK_STATUS_CODE_INVALID: a.LoaderErrors.HTTP_STATUS_CODE_INVALID, NETWORK_TIMEOUT: a.LoaderErrors.CONNECTING_TIMEOUT, NETWORK_UNRECOVERABLE_EARLY_EOF: a.LoaderErrors.UNRECOVERABLE_EARLY_EOF, MEDIA_MSE_ERROR: "MediaMSEError", MEDIA_FORMAT_ERROR: n.default.FORMAT_ERROR, MEDIA_FORMAT_UNSUPPORTED: n.default.FORMAT_UNSUPPORTED, MEDIA_CODEC_UNSUPPORTED: n.default.CODEC_UNSUPPORTED };
          }, 994: function(e2, t2, i2) {
            "use strict";
            i2.r(t2);
            var a = {};
            !(function() {
              var e3 = self.navigator.userAgent.toLowerCase(), t3 = /(edge)\/([\w.]+)/.exec(e3) || /(opr)[\/]([\w.]+)/.exec(e3) || /(chrome)[ \/]([\w.]+)/.exec(e3) || /(iemobile)[\/]([\w.]+)/.exec(e3) || /(version)(applewebkit)[ \/]([\w.]+).*(safari)[ \/]([\w.]+)/.exec(e3) || /(webkit)[ \/]([\w.]+).*(version)[ \/]([\w.]+).*(safari)[ \/]([\w.]+)/.exec(e3) || /(webkit)[ \/]([\w.]+)/.exec(e3) || /(opera)(?:.*version|)[ \/]([\w.]+)/.exec(e3) || /(msie) ([\w.]+)/.exec(e3) || e3.indexOf("trident") >= 0 && /(rv)(?::| )([\w.]+)/.exec(e3) || e3.indexOf("compatible") < 0 && /(firefox)[ \/]([\w.]+)/.exec(e3) || [], i3 = /(ipad)/.exec(e3) || /(ipod)/.exec(e3) || /(windows phone)/.exec(e3) || /(iphone)/.exec(e3) || /(kindle)/.exec(e3) || /(android)/.exec(e3) || /(windows)/.exec(e3) || /(mac)/.exec(e3) || /(linux)/.exec(e3) || /(cros)/.exec(e3) || [], n = { browser: t3[5] || t3[3] || t3[1] || "", version: t3[2] || t3[4] || "0", majorVersion: t3[4] || t3[2] || "0", platform: i3[0] || "" }, r = {};
              if (n.browser) {
                r[n.browser] = true;
                var o = n.majorVersion.split(".");
                r.version = { major: parseInt(n.majorVersion, 10), string: n.version }, o.length > 1 && (r.version.minor = parseInt(o[1], 10)), o.length > 2 && (r.version.build = parseInt(o[2], 10));
              }
              if (n.platform && (r[n.platform] = true), (r.chrome || r.opr || r.safari) && (r.webkit = true), r.rv || r.iemobile) {
                r.rv && delete r.rv;
                var s = "msie";
                n.browser = s, r[s] = true;
              }
              if (r.edge) {
                delete r.edge;
                var d = "msedge";
                n.browser = d, r[d] = true;
              }
              if (r.opr) {
                var _ = "opera";
                n.browser = _, r[_] = true;
              }
              if (r.safari && r.android) {
                var l = "android";
                n.browser = l, r[l] = true;
              }
              for (var u in r.name = n.browser, r.platform = n.platform, a) a.hasOwnProperty(u) && delete a[u];
              Object.assign(a, r);
            })(), t2.default = a;
          }, 867: function(e2, t2, i2) {
            "use strict";
            i2.r(t2), i2.d(t2, { IllegalStateException: function() {
              return o;
            }, InvalidArgumentException: function() {
              return s;
            }, NotImplementedException: function() {
              return d;
            }, RuntimeException: function() {
              return r;
            } });
            var a, n = (a = function(e3, t3) {
              return a = Object.setPrototypeOf || { __proto__: [] } instanceof Array && function(e4, t4) {
                e4.__proto__ = t4;
              } || function(e4, t4) {
                for (var i3 in t4) Object.prototype.hasOwnProperty.call(t4, i3) && (e4[i3] = t4[i3]);
              }, a(e3, t3);
            }, function(e3, t3) {
              if ("function" != typeof t3 && null !== t3) throw new TypeError("Class extends value " + String(t3) + " is not a constructor or null");
              function i3() {
                this.constructor = e3;
              }
              a(e3, t3), e3.prototype = null === t3 ? Object.create(t3) : (i3.prototype = t3.prototype, new i3());
            }), r = (function() {
              function e3(e4) {
                this._message = e4;
              }
              return Object.defineProperty(e3.prototype, "name", { get: function() {
                return "RuntimeException";
              }, enumerable: false, configurable: true }), Object.defineProperty(e3.prototype, "message", { get: function() {
                return this._message;
              }, enumerable: false, configurable: true }), e3.prototype.toString = function() {
                return this.name + ": " + this.message;
              }, e3;
            })(), o = (function(e3) {
              function t3(t4) {
                return e3.call(this, t4) || this;
              }
              return n(t3, e3), Object.defineProperty(t3.prototype, "name", { get: function() {
                return "IllegalStateException";
              }, enumerable: false, configurable: true }), t3;
            })(r), s = (function(e3) {
              function t3(t4) {
                return e3.call(this, t4) || this;
              }
              return n(t3, e3), Object.defineProperty(t3.prototype, "name", { get: function() {
                return "InvalidArgumentException";
              }, enumerable: false, configurable: true }), t3;
            })(r), d = (function(e3) {
              function t3(t4) {
                return e3.call(this, t4) || this;
              }
              return n(t3, e3), Object.defineProperty(t3.prototype, "name", { get: function() {
                return "NotImplementedException";
              }, enumerable: false, configurable: true }), t3;
            })(r);
          }, 856: function(e2, t2, i2) {
            "use strict";
            i2.r(t2);
            var a = i2(7), n = i2.n(a), r = (function() {
              function e3() {
              }
              return e3.e = function(t3, i3) {
                t3 && !e3.FORCE_GLOBAL_TAG || (t3 = e3.GLOBAL_TAG);
                var a2 = "[".concat(t3, "] > ").concat(i3);
                e3.ENABLE_CALLBACK && e3.emitter.emit("log", "error", a2), e3.ENABLE_ERROR && (console.error ? console.error(a2) : console.warn ? console.warn(a2) : console.log(a2));
              }, e3.i = function(t3, i3) {
                t3 && !e3.FORCE_GLOBAL_TAG || (t3 = e3.GLOBAL_TAG);
                var a2 = "[".concat(t3, "] > ").concat(i3);
                e3.ENABLE_CALLBACK && e3.emitter.emit("log", "info", a2), e3.ENABLE_INFO && (console.info ? console.info(a2) : console.log(a2));
              }, e3.w = function(t3, i3) {
                t3 && !e3.FORCE_GLOBAL_TAG || (t3 = e3.GLOBAL_TAG);
                var a2 = "[".concat(t3, "] > ").concat(i3);
                e3.ENABLE_CALLBACK && e3.emitter.emit("log", "warn", a2), e3.ENABLE_WARN && (console.warn ? console.warn(a2) : console.log(a2));
              }, e3.d = function(t3, i3) {
                t3 && !e3.FORCE_GLOBAL_TAG || (t3 = e3.GLOBAL_TAG);
                var a2 = "[".concat(t3, "] > ").concat(i3);
                e3.ENABLE_CALLBACK && e3.emitter.emit("log", "debug", a2), e3.ENABLE_DEBUG && (console.debug ? console.debug(a2) : console.log(a2));
              }, e3.v = function(t3, i3) {
                t3 && !e3.FORCE_GLOBAL_TAG || (t3 = e3.GLOBAL_TAG);
                var a2 = "[".concat(t3, "] > ").concat(i3);
                e3.ENABLE_CALLBACK && e3.emitter.emit("log", "verbose", a2), e3.ENABLE_VERBOSE && console.log(a2);
              }, e3;
            })();
            r.GLOBAL_TAG = "mpegts.js", r.FORCE_GLOBAL_TAG = false, r.ENABLE_ERROR = true, r.ENABLE_INFO = true, r.ENABLE_WARN = true, r.ENABLE_DEBUG = true, r.ENABLE_VERBOSE = true, r.ENABLE_CALLBACK = false, r.emitter = new (n())(), t2.default = r;
          }, 947: function(e2, t2, i2) {
            "use strict";
            i2.r(t2);
            var a = i2(7), n = i2.n(a), r = i2(856), o = (function() {
              function e3() {
              }
              return Object.defineProperty(e3, "forceGlobalTag", { get: function() {
                return r.default.FORCE_GLOBAL_TAG;
              }, set: function(t3) {
                r.default.FORCE_GLOBAL_TAG = t3, e3._notifyChange();
              }, enumerable: false, configurable: true }), Object.defineProperty(e3, "globalTag", { get: function() {
                return r.default.GLOBAL_TAG;
              }, set: function(t3) {
                r.default.GLOBAL_TAG = t3, e3._notifyChange();
              }, enumerable: false, configurable: true }), Object.defineProperty(e3, "enableAll", { get: function() {
                return r.default.ENABLE_VERBOSE && r.default.ENABLE_DEBUG && r.default.ENABLE_INFO && r.default.ENABLE_WARN && r.default.ENABLE_ERROR;
              }, set: function(t3) {
                r.default.ENABLE_VERBOSE = t3, r.default.ENABLE_DEBUG = t3, r.default.ENABLE_INFO = t3, r.default.ENABLE_WARN = t3, r.default.ENABLE_ERROR = t3, e3._notifyChange();
              }, enumerable: false, configurable: true }), Object.defineProperty(e3, "enableDebug", { get: function() {
                return r.default.ENABLE_DEBUG;
              }, set: function(t3) {
                r.default.ENABLE_DEBUG = t3, e3._notifyChange();
              }, enumerable: false, configurable: true }), Object.defineProperty(e3, "enableVerbose", { get: function() {
                return r.default.ENABLE_VERBOSE;
              }, set: function(t3) {
                r.default.ENABLE_VERBOSE = t3, e3._notifyChange();
              }, enumerable: false, configurable: true }), Object.defineProperty(e3, "enableInfo", { get: function() {
                return r.default.ENABLE_INFO;
              }, set: function(t3) {
                r.default.ENABLE_INFO = t3, e3._notifyChange();
              }, enumerable: false, configurable: true }), Object.defineProperty(e3, "enableWarn", { get: function() {
                return r.default.ENABLE_WARN;
              }, set: function(t3) {
                r.default.ENABLE_WARN = t3, e3._notifyChange();
              }, enumerable: false, configurable: true }), Object.defineProperty(e3, "enableError", { get: function() {
                return r.default.ENABLE_ERROR;
              }, set: function(t3) {
                r.default.ENABLE_ERROR = t3, e3._notifyChange();
              }, enumerable: false, configurable: true }), e3.getConfig = function() {
                return { globalTag: r.default.GLOBAL_TAG, forceGlobalTag: r.default.FORCE_GLOBAL_TAG, enableVerbose: r.default.ENABLE_VERBOSE, enableDebug: r.default.ENABLE_DEBUG, enableInfo: r.default.ENABLE_INFO, enableWarn: r.default.ENABLE_WARN, enableError: r.default.ENABLE_ERROR, enableCallback: r.default.ENABLE_CALLBACK };
              }, e3.applyConfig = function(e4) {
                r.default.GLOBAL_TAG = e4.globalTag, r.default.FORCE_GLOBAL_TAG = e4.forceGlobalTag, r.default.ENABLE_VERBOSE = e4.enableVerbose, r.default.ENABLE_DEBUG = e4.enableDebug, r.default.ENABLE_INFO = e4.enableInfo, r.default.ENABLE_WARN = e4.enableWarn, r.default.ENABLE_ERROR = e4.enableError, r.default.ENABLE_CALLBACK = e4.enableCallback;
              }, e3._notifyChange = function() {
                var t3 = e3.emitter;
                if (t3.listenerCount("change") > 0) {
                  var i3 = e3.getConfig();
                  t3.emit("change", i3);
                }
              }, e3.registerListener = function(t3) {
                e3.emitter.addListener("change", t3);
              }, e3.removeListener = function(t3) {
                e3.emitter.removeListener("change", t3);
              }, e3.addLogListener = function(t3) {
                r.default.emitter.addListener("log", t3), r.default.emitter.listenerCount("log") > 0 && (r.default.ENABLE_CALLBACK = true, e3._notifyChange());
              }, e3.removeLogListener = function(t3) {
                r.default.emitter.removeListener("log", t3), 0 === r.default.emitter.listenerCount("log") && (r.default.ENABLE_CALLBACK = false, e3._notifyChange());
              }, e3;
            })();
            o.emitter = new (n())(), t2.default = o;
          }, 811: function(e2, t2, i2) {
            "use strict";
            i2.r(t2);
            var a = (function() {
              function e3() {
              }
              return e3.install = function() {
                Object.setPrototypeOf = Object.setPrototypeOf || function(e4, t3) {
                  return e4.__proto__ = t3, e4;
                }, Object.assign = Object.assign || function(e4) {
                  if (null == e4) throw new TypeError("Cannot convert undefined or null to object");
                  for (var t3 = Object(e4), i3 = 1; i3 < arguments.length; i3++) {
                    var a2 = arguments[i3];
                    if (null != a2) for (var n in a2) a2.hasOwnProperty(n) && (t3[n] = a2[n]);
                  }
                  return t3;
                }, String.prototype.startsWith || Object.defineProperty(String.prototype, "startsWith", { value: function(e4, t3) {
                  var i3 = t3 > 0 ? 0 | t3 : 0;
                  return this.substring(i3, i3 + e4.length) === e4;
                } }), "function" != typeof self.Promise && i2(964).polyfill();
              }, e3;
            })();
            a.install(), t2.default = a;
          }, 861: function(e2, t2, i2) {
            function a(e3) {
              var t3 = {};
              function i3(a3) {
                if (t3[a3]) return t3[a3].exports;
                var n2 = t3[a3] = { i: a3, id: a3, l: false, loaded: false, exports: {} };
                return e3[a3].call(n2.exports, n2, n2.exports, i3), n2.l = true, n2.loaded = true, n2.exports;
              }
              i3.m = e3, i3.c = t3, i3.d = function(e4, t4) {
                for (var a3 in t4) i3.o(t4, a3) && !i3.o(e4, a3) && Object.defineProperty(e4, a3, { enumerable: true, get: t4[a3] });
              }, i3.r = function(e4) {
                "undefined" != typeof Symbol && Symbol.toStringTag && Object.defineProperty(e4, Symbol.toStringTag, { value: "Module" }), Object.defineProperty(e4, "__esModule", { value: true });
              }, i3.n = function(e4) {
                var t4 = e4 && e4.__esModule ? function() {
                  return e4.default;
                } : function() {
                  return e4;
                };
                return i3.d(t4, { a: t4 }), t4;
              }, i3.o = function(e4, t4) {
                return Object.prototype.hasOwnProperty.call(e4, t4);
              }, i3.g = (function() {
                if ("object" == typeof globalThis) return globalThis;
                try {
                  return this || new Function("return this")();
                } catch (e4) {
                  if ("object" == typeof self) return self;
                }
              })(), i3.p = "/";
              var a2 = i3(ENTRY_MODULE);
              return a2.default || a2;
            }
            var n = "[\\.|\\-|\\+|\\w|/|@]+", r = "\\(\\s*(/\\*.*?\\*/)?\\s*.*?(" + n + ").*?\\)";
            function o(e3) {
              return (e3 + "").replace(/[.?*+^$[\]\\(){}|-]/g, "\\$&");
            }
            function s(e3) {
              return !isNaN(1 * e3);
            }
            function d(e3, t3, a2) {
              var d2 = {};
              d2[a2] = [];
              var _2 = t3.toString(), l = _2.match(/^(?:function\s*\w*\s*)?\(\s*\w+\s*,\s*\w+\s*,\s*(\w+)\s*\)/);
              if (!l) return d2;
              for (var u, c = l[1], h = new RegExp("(\\\\n|\\W)" + o(c) + r, "g"); u = h.exec(_2); ) "dll-reference" !== u[3] && d2[a2].push(u[3]);
              for (h = new RegExp("\\(" + o(c) + '\\("(dll-reference\\s(' + n + '))"\\)\\)' + r, "g"); u = h.exec(_2); ) e3[u[2]] || (d2[a2].push(u[1]), e3[u[2]] = i2(u[1]).m), d2[u[2]] = d2[u[2]] || [], d2[u[2]].push(u[4]);
              for (var f = Object.keys(d2), p = 0; p < f.length; p++) for (var m = 0; m < d2[f[p]].length; m++) s(d2[f[p]][m]) && (d2[f[p]][m] = 1 * d2[f[p]][m]);
              return d2;
            }
            function _(e3) {
              return Object.keys(e3).reduce(function(t3, i3) {
                return t3 || e3[i3].length > 0;
              }, false);
            }
            e2.exports = function(e3, t3) {
              t3 = t3 || {};
              var n2 = { main: i2.m }, r2 = t3.all ? { main: Object.keys(n2.main) } : (function(e4, t4) {
                for (var i3 = { main: [t4] }, a2 = { main: [] }, n3 = { main: {} }; _(i3); ) for (var r3 = Object.keys(i3), o3 = 0; o3 < r3.length; o3++) {
                  var s3 = r3[o3], l2 = i3[s3].pop();
                  if (n3[s3] = n3[s3] || {}, !n3[s3][l2] && e4[s3][l2]) {
                    n3[s3][l2] = true, a2[s3] = a2[s3] || [], a2[s3].push(l2);
                    for (var u2 = d(e4, e4[s3][l2], s3), c = Object.keys(u2), h = 0; h < c.length; h++) i3[c[h]] = i3[c[h]] || [], i3[c[h]] = i3[c[h]].concat(u2[c[h]]);
                  }
                }
                return a2;
              })(n2, e3), o2 = "";
              Object.keys(r2).filter(function(e4) {
                return "main" !== e4;
              }).forEach(function(e4) {
                for (var t4 = 0; r2[e4][t4]; ) t4++;
                r2[e4].push(t4), n2[e4][t4] = "(function(module, exports, __webpack_require__) { module.exports = __webpack_require__; })", o2 = o2 + "var " + e4 + " = (" + a.toString().replace("ENTRY_MODULE", JSON.stringify(t4)) + ")({" + r2[e4].map(function(t5) {
                  return JSON.stringify(t5) + ": " + n2[e4][t5].toString();
                }).join(",") + "});\n";
              }), o2 = o2 + "new ((" + a.toString().replace("ENTRY_MODULE", JSON.stringify(e3)) + ")({" + r2.main.map(function(e4) {
                return JSON.stringify(e4) + ": " + n2.main[e4].toString();
              }).join(",") + "}))(self);";
              var s2 = new self.Blob([o2], { type: "text/javascript" });
              if (t3.bare) return s2;
              var l = (self.URL || self.webkitURL || self.mozURL || self.msURL).createObjectURL(s2), u = new self.Worker(l);
              return u.objectURL = l, u;
            };
          }, 403: function(e2, t2, i2) {
            "use strict";
            var a;
            i2.r(t2), (function(e3) {
              e3.ERROR = "error", e3.SOURCE_OPEN = "source_open", e3.UPDATE_END = "update_end", e3.BUFFER_FULL = "buffer_full", e3.START_STREAMING = "start_streaming", e3.END_STREAMING = "end_streaming";
            })(a || (a = {})), t2.default = a;
          }, 726: function(e2, t2, i2) {
            "use strict";
            var a;
            i2.r(t2), (function(e3) {
              e3.IO_ERROR = "io_error", e3.DEMUX_ERROR = "demux_error", e3.INIT_SEGMENT = "init_segment", e3.MEDIA_SEGMENT = "media_segment", e3.LOADING_COMPLETE = "loading_complete", e3.RECOVERED_EARLY_EOF = "recovered_early_eof", e3.MEDIA_INFO = "media_info", e3.METADATA_ARRIVED = "metadata_arrived", e3.SCRIPTDATA_ARRIVED = "scriptdata_arrived", e3.TIMED_ID3_METADATA_ARRIVED = "timed_id3_metadata_arrived", e3.PGS_SUBTITLE_ARRIVED = "pgs_subtitle_arrived", e3.SYNCHRONOUS_KLV_METADATA_ARRIVED = "synchronous_klv_metadata_arrived", e3.ASYNCHRONOUS_KLV_METADATA_ARRIVED = "asynchronous_klv_metadata_arrived", e3.SMPTE2038_METADATA_ARRIVED = "smpte2038_metadata_arrived", e3.SEI_ARRIVED = "sei_arrived", e3.SCTE35_METADATA_ARRIVED = "scte35_metadata_arrived", e3.PES_PRIVATE_DATA_DESCRIPTOR = "pes_private_data_descriptor", e3.PES_PRIVATE_DATA_ARRIVED = "pes_private_data_arrived", e3.STATISTICS_INFO = "statistics_info", e3.RECOMMEND_SEEKPOINT = "recommend_seekpoint";
            })(a || (a = {})), t2.default = a;
          }, 877: function(e2, t2, i2) {
            "use strict";
            i2.r(t2);
            var a = i2(856), n = i2(947), r = i2(867), o = i2(403), s = i2(346), d = i2(527), _ = i2(726), l = i2(117), u = i2(355);
            t2.default = function(e3) {
              var t3 = "PlayerEngineWorker", i3 = function(t4, i4) {
                e3.postMessage({ msg: "logcat_callback", type: t4, logcat: i4 });
              }.bind(void 0), c = null, h = null, f = null, p = null, m = false, g = false, v = 0, y = 0, S = false;
              function E() {
                f && (f.shutdown(), f.destroy(), f = null);
              }
              function A() {
                if (null == c || null == h) throw new r.IllegalStateException("Worker not initialized");
                if (p) throw new r.IllegalStateException("Transmuxer has been initialized");
                g || (!h.deferLoadAfterSourceOpen || m ? ((p = new d.default(c, h)).on(_.default.INIT_SEGMENT, function(e4, t4) {
                  f.appendInitSegment(t4);
                }), p.on(_.default.MEDIA_SEGMENT, function(t4, i4) {
                  f.appendMediaSegment(i4), e3.postMessage({ msg: "buffered_position_changed", buffered_position_milliseconds: i4.info.endDts });
                }), p.on(_.default.LOADING_COMPLETE, function() {
                  f.endOfStream(), e3.postMessage({ msg: "player_event", event: l.default.LOADING_COMPLETE });
                }), p.on(_.default.RECOVERED_EARLY_EOF, function() {
                  e3.postMessage({ msg: "player_event", event: l.default.RECOVERED_EARLY_EOF });
                }), p.on(_.default.IO_ERROR, function(t4, i4) {
                  e3.postMessage({ msg: "player_event", event: l.default.ERROR, error_type: u.ErrorTypes.NETWORK_ERROR, error_detail: t4, info: i4 });
                }), p.on(_.default.DEMUX_ERROR, function(t4, i4) {
                  e3.postMessage({ msg: "player_event", event: l.default.ERROR, error_type: u.ErrorTypes.MEDIA_ERROR, error_detail: t4, info: i4 });
                }), p.on(_.default.MEDIA_INFO, function(e4) {
                  D(_.default.MEDIA_INFO, e4);
                }), p.on(_.default.STATISTICS_INFO, function(e4) {
                  D(_.default.STATISTICS_INFO, e4);
                }), p.on(_.default.RECOMMEND_SEEKPOINT, function(t4) {
                  !(function(t5) {
                    e3.postMessage({ msg: "transmuxing_event", event: _.default.RECOMMEND_SEEKPOINT, milliseconds: t5 });
                  })(t4);
                }), p.on(_.default.METADATA_ARRIVED, function(e4) {
                  M(l.default.METADATA_ARRIVED, e4);
                }), p.on(_.default.SCRIPTDATA_ARRIVED, function(e4) {
                  M(l.default.SCRIPTDATA_ARRIVED, e4);
                }), p.on(_.default.TIMED_ID3_METADATA_ARRIVED, function(e4) {
                  M(l.default.TIMED_ID3_METADATA_ARRIVED, e4);
                }), p.on(_.default.PGS_SUBTITLE_ARRIVED, function(e4) {
                  M(l.default.PGS_SUBTITLE_ARRIVED, e4);
                }), p.on(_.default.SYNCHRONOUS_KLV_METADATA_ARRIVED, function(e4) {
                  M(l.default.SYNCHRONOUS_KLV_METADATA_ARRIVED, e4);
                }), p.on(_.default.ASYNCHRONOUS_KLV_METADATA_ARRIVED, function(e4) {
                  M(l.default.ASYNCHRONOUS_KLV_METADATA_ARRIVED, e4);
                }), p.on(_.default.SMPTE2038_METADATA_ARRIVED, function(e4) {
                  M(l.default.SMPTE2038_METADATA_ARRIVED, e4);
                }), p.on(_.default.SEI_ARRIVED, function(e4) {
                  M(l.default.SEI_ARRIVED, e4);
                }), p.on(_.default.SCTE35_METADATA_ARRIVED, function(e4) {
                  M(l.default.SCTE35_METADATA_ARRIVED, e4);
                }), p.on(_.default.PES_PRIVATE_DATA_DESCRIPTOR, function(e4) {
                  M(l.default.PES_PRIVATE_DATA_DESCRIPTOR, e4);
                }), p.on(_.default.PES_PRIVATE_DATA_ARRIVED, function(e4) {
                  M(l.default.PES_PRIVATE_DATA_ARRIVED, e4);
                }), p.open()) : g = true);
              }
              function b() {
                f && f.flush(), p && (p.close(), p.destroy(), p = null);
              }
              function R() {
                m = true, g && (g = false, A());
              }
              function T() {
                e3.postMessage({ msg: "mse_event", event: o.default.UPDATE_END });
              }
              function L() {
                a.default.v(t3, "MSE SourceBuffer is full, report to main thread"), e3.postMessage({ msg: "mse_event", event: o.default.BUFFER_FULL });
              }
              function k(t4) {
                e3.postMessage({ msg: "player_event", event: l.default.ERROR, error_type: u.ErrorTypes.MEDIA_ERROR, error_detail: u.ErrorTypes.MEDIA_MSE_ERROR, info: t4 });
              }
              function D(t4, i4) {
                e3.postMessage({ msg: "transmuxing_event", event: t4, info: i4 });
              }
              function M(t4, i4) {
                e3.postMessage({ msg: "player_event", event: t4, extraData: i4 });
              }
              e3.addEventListener("message", function(r2) {
                if (!S) {
                  var d2 = r2.data;
                  switch (d2.cmd) {
                    case "logging_config":
                      var _2 = d2;
                      n.default.applyConfig(_2.logging_config), true === _2.logging_config.enableCallback ? n.default.addLogListener(i3) : n.default.removeLogListener(i3);
                      break;
                    case "init":
                      c = (_2 = d2).media_data_source, h = _2.config;
                      break;
                    case "destroy":
                      p && b(), f && E(), S = true, e3.postMessage({ msg: "destroyed" });
                      break;
                    case "initialize_mse":
                      !(function() {
                        a.default.v(t3, "Initializing MediaSource in DedicatedWorker"), (f = new s.default(h)).on(o.default.SOURCE_OPEN, R.bind(this)), f.on(o.default.UPDATE_END, T.bind(this)), f.on(o.default.BUFFER_FULL, L.bind(this)), f.on(o.default.ERROR, k.bind(this)), f.initialize({ getCurrentTime: function() {
                          return v;
                        }, getReadyState: function() {
                          return y;
                        } });
                        var i4 = f.getHandle();
                        e3.postMessage({ msg: "mse_init", handle: i4 }, [i4]);
                      })();
                      break;
                    case "shutdown_mse":
                      E();
                      break;
                    case "load":
                      A();
                      break;
                    case "unload":
                      b();
                      break;
                    case "unbuffered_seek":
                      _2 = d2, f.flush(), p.seek(_2.milliseconds);
                      break;
                    case "timeupdate":
                      v = (_2 = d2).current_time;
                      break;
                    case "readystatechange":
                      y = (_2 = d2).ready_state;
                      break;
                    case "pause_transmuxer":
                      p.pause();
                      break;
                    case "resume_transmuxer":
                      p.resume();
                  }
                }
              });
            };
          }, 117: function(e2, t2, i2) {
            "use strict";
            var a;
            i2.r(t2), (function(e3) {
              e3.ERROR = "error", e3.LOADING_COMPLETE = "loading_complete", e3.RECOVERED_EARLY_EOF = "recovered_early_eof", e3.MEDIA_INFO = "media_info", e3.METADATA_ARRIVED = "metadata_arrived", e3.SCRIPTDATA_ARRIVED = "scriptdata_arrived", e3.TIMED_ID3_METADATA_ARRIVED = "timed_id3_metadata_arrived", e3.PGS_SUBTITLE_ARRIVED = "pgs_subtitle_arrived", e3.SYNCHRONOUS_KLV_METADATA_ARRIVED = "synchronous_klv_metadata_arrived", e3.ASYNCHRONOUS_KLV_METADATA_ARRIVED = "asynchronous_klv_metadata_arrived", e3.SMPTE2038_METADATA_ARRIVED = "smpte2038_metadata_arrived", e3.SEI_ARRIVED = "sei_arrived", e3.SCTE35_METADATA_ARRIVED = "scte35_metadata_arrived", e3.PES_PRIVATE_DATA_DESCRIPTOR = "pes_private_data_descriptor", e3.PES_PRIVATE_DATA_ARRIVED = "pes_private_data_arrived", e3.STATISTICS_INFO = "statistics_info", e3.DESTROYING = "destroying";
            })(a || (a = {})), t2.default = a;
          } }, t = {};
          function i(a) {
            var n = t[a];
            if (void 0 !== n) return n.exports;
            var r = t[a] = { exports: {} };
            return e[a].call(r.exports, r, r.exports, i), r.exports;
          }
          return i.m = e, i.n = function(e2) {
            var t2 = e2 && e2.__esModule ? function() {
              return e2.default;
            } : function() {
              return e2;
            };
            return i.d(t2, { a: t2 }), t2;
          }, i.d = function(e2, t2) {
            if (Array.isArray(t2)) for (var a = 0; a < t2.length; ) {
              var n = t2[a++], r = t2[a++];
              i.o(e2, n) ? 0 === r && a++ : 0 === r ? Object.defineProperty(e2, n, { enumerable: true, value: t2[a++] }) : Object.defineProperty(e2, n, { enumerable: true, get: r });
            }
            else for (var n in t2) i.o(t2, n) && !i.o(e2, n) && Object.defineProperty(e2, n, { enumerable: true, get: t2[n] });
          }, i.g = (function() {
            if ("object" == typeof globalThis) return globalThis;
            try {
              return this || new Function("return this")();
            } catch (e2) {
              if ("object" == typeof window) return window;
            }
          })(), i.o = function(e2, t2) {
            return Object.prototype.hasOwnProperty.call(e2, t2);
          }, i.r = function(e2) {
            "undefined" != typeof Symbol && Symbol.toStringTag && Object.defineProperty(e2, Symbol.toStringTag, { value: "Module" }), Object.defineProperty(e2, "__esModule", { value: true });
          }, i(976);
        })();
      });
    }
  });

  // src/production/operator-console/renderer/main.ts
  var import_mpegts = __toESM(require_mpegts(), 1);

  // src/production/operation-workflow/flight-controller-video-hint/index.ts
  var freeze = (value) => Object.freeze(value);
  var FLIGHT_CONTROLLER_VIDEO_HINT = "\u98DE\u63A7\u5C1A\u672A\u8FDE\u63A5\u3002\u8BF7\u8F6C\u52A8\u9065\u63A7\u5668\u6447\u6746\u6216\u91CD\u542F\u98DE\u673A\uFF1B\u8FDE\u4E0A\u540E\u518D\u70B9\u4E00\u6B21\u5F00\u59CB\u3002";
  var evaluate = (input) => {
    const neverConnected = input.hasConnectedOnce === false;
    const banner = neverConnected ? FLIGHT_CONTROLLER_VIDEO_HINT : null;
    const fpsIsZero = input.fps === 0;
    const streaming = input.streaming === true;
    const afterStart = neverConnected && streaming && fpsIsZero ? FLIGHT_CONTROLLER_VIDEO_HINT : null;
    return freeze({ banner, afterStart });
  };
  var FlightControllerVideoHint = freeze({ evaluate });

  // src/modules/device-console/device-guidance/index.ts
  var freeze2 = (value) => Object.freeze(value);
  var issue = (field, reason) => freeze2({ field, reason });
  function failure(field, reason) {
    return freeze2({ ok: false, error: freeze2({ code: "INVALID_INPUT", details: freeze2({ field, reason }) }) });
  }
  function validDeviceId(value) {
    return typeof value === "string" && value.trim().length > 0 && Array.from(value).length <= 128 && !/[\p{Cc}]/u.test(value);
  }
  function isStatus(value) {
    return value === "connected" || value === "disconnected" || value === "unknown";
  }
  function isOverall(value) {
    return value === "ready" || value === "degraded" || value === "offline";
  }
  function hasConsistentStates(link) {
    if (link.computerToPhone === "disconnected") return link.overall === "offline" && link.phoneToRemoteController === "unknown" && link.remoteControllerToAircraft === "unknown";
    if (link.phoneToRemoteController === "unknown") return link.overall === "degraded" && link.remoteControllerToAircraft === "unknown";
    if (link.phoneToRemoteController === "disconnected") return link.overall === "degraded" && link.remoteControllerToAircraft === "unknown";
    if (link.remoteControllerToAircraft === "connected") return link.overall === "ready";
    return link.overall === "degraded";
  }
  function readLink(value) {
    if (value === null || typeof value !== "object") return issue("link", "invalid-container");
    try {
      const candidate = value;
      if (!validDeviceId(candidate.deviceId)) return issue("link.deviceId", "invalid-value");
      if (!isOverall(candidate.overall)) return issue("link.overall", "invalid-value");
      if (candidate.computerToPhone !== "connected" && candidate.computerToPhone !== "disconnected") return issue("link.computerToPhone", "invalid-value");
      if (!isStatus(candidate.phoneToRemoteController)) return issue("link.phoneToRemoteController", "invalid-value");
      if (!isStatus(candidate.remoteControllerToAircraft)) return issue("link.remoteControllerToAircraft", "invalid-value");
      const link = freeze2({ deviceId: candidate.deviceId, overall: candidate.overall, computerToPhone: candidate.computerToPhone, phoneToRemoteController: candidate.phoneToRemoteController, remoteControllerToAircraft: candidate.remoteControllerToAircraft });
      return hasConsistentStates(link) ? link : issue("link", "invalid-value");
    } catch {
      return issue("link", "unreadable");
    }
  }
  function readInput(value) {
    if (value === null || typeof value !== "object") return issue("input", "invalid-container");
    try {
      const input = value;
      return freeze2({ link: input.link });
    } catch {
      return issue("input", "unreadable");
    }
  }
  function snapshot(deviceId, code, action, title, message) {
    return freeze2({ ok: true, value: freeze2({ deviceId, code, action, title, message }) });
  }
  function evaluate2(value) {
    const input = readInput(value);
    if ("field" in input) return failure(input.field, input.reason);
    const link = readLink(input.link);
    if ("field" in link) return failure(link.field, link.reason);
    if (link.computerToPhone === "disconnected") return snapshot(link.deviceId, "CONNECT_PHONE", "reconnect-phone", "\u8FDE\u63A5\u624B\u673A", "\u8BF7\u5728\u624B\u673A\u4E0A\u91CD\u65B0\u8FDE\u63A5\u3002");
    if (link.phoneToRemoteController === "unknown") return snapshot(link.deviceId, "WAIT_FOR_SDK", "wait-for-sdk", "\u7B49\u5F85\u8BBE\u5907\u72B6\u6001", "\u6B63\u5728\u786E\u8BA4\u9065\u63A7\u5668\u548C\u98DE\u673A\u7684\u8FDE\u63A5\u72B6\u6001\u3002");
    if (link.phoneToRemoteController === "disconnected") return snapshot(link.deviceId, "CONNECT_REMOTE_CONTROLLER", "connect-remote-controller", "\u8FDE\u63A5\u9065\u63A7\u5668", "\u8BF7\u6253\u5F00\u5E76\u8FDE\u63A5\u9065\u63A7\u5668\u3002");
    if (link.remoteControllerToAircraft === "unknown") return snapshot(link.deviceId, "WAIT_FOR_SDK", "wait-for-sdk", "\u7B49\u5F85\u8BBE\u5907\u72B6\u6001", "\u6B63\u5728\u786E\u8BA4\u9065\u63A7\u5668\u548C\u98DE\u673A\u7684\u8FDE\u63A5\u72B6\u6001\u3002");
    if (link.remoteControllerToAircraft === "connected") {
      return snapshot(link.deviceId, "READY", null, "\u8BBE\u5907\u5DF2\u5C31\u7EEA", "\u624B\u673A\u3001\u9065\u63A7\u5668\u548C\u98DE\u673A\u5747\u5DF2\u8FDE\u63A5\u3002");
    }
    return snapshot(link.deviceId, "CONNECT_AIRCRAFT", "connect-aircraft", "\u7B49\u5F85\u98DE\u673A", "\u8BF7\u6253\u5F00\u98DE\u673A\u5E76\u7B49\u5F85\u5176\u8FDE\u63A5\u3002");
  }
  var DeviceGuidance = Object.freeze({ evaluate: evaluate2 });

  // src/modules/device-console/link-chain/index.ts
  var freeze3 = (value) => Object.freeze(value);
  var success = (value) => freeze3({ ok: true, value });
  var failure2 = (field, reason) => freeze3({ ok: false, error: freeze3({ code: "INVALID_INPUT", details: freeze3({ field, reason }) }) });
  function validDeviceId2(value) {
    return typeof value === "string" && value.trim().length > 0 && Array.from(value).length <= 128 && !/[\p{Cc}]/u.test(value);
  }
  function readInput2(value) {
    if (value === null || typeof value !== "object") return "invalid-container";
    try {
      const input = value;
      return freeze3({ deviceId: input.deviceId, relayConnected: input.relayConnected, telemetry: input.telemetry });
    } catch {
      return "unreadable";
    }
  }
  function readTelemetry(value) {
    if (value === null) return null;
    if (typeof value !== "object") return freeze3({ field: "telemetry", reason: "invalid-container" });
    try {
      const telemetry = value;
      const sdk = telemetry.sdkAvailability;
      if (sdk !== void 0 && sdk !== "STOPPED" && sdk !== "STARTING" && sdk !== "READY" && sdk !== "FAILED" && sdk !== "UNKNOWN") return freeze3({ field: "telemetry.sdkAvailability", reason: "invalid-type" });
      const remote = telemetry.remoteController;
      if (remote !== void 0 && remote !== "CONNECTED" && remote !== "DISCONNECTED" && remote !== "UNKNOWN") return freeze3({ field: "telemetry.remoteController", reason: "invalid-type" });
      const flight = telemetry.flightController;
      if (flight !== void 0 && flight !== "CONNECTED" && flight !== "DISCONNECTED" && flight !== "UNKNOWN") return freeze3({ field: "telemetry.flightController", reason: "invalid-type" });
      const fields = ["sdkRegistered", "remoteControllerConnected", "flightControllerConnected"];
      for (const field of fields) if (telemetry[field] !== void 0 && typeof telemetry[field] !== "boolean") return freeze3({ field: `telemetry.${field}`, reason: "invalid-type" });
      return freeze3({
        ...sdk === void 0 ? {} : { sdkAvailability: sdk },
        ...remote === void 0 ? {} : { remoteController: remote },
        ...flight === void 0 ? {} : { flightController: flight },
        // Stryker disable next-line ConditionalExpression: omitted optional telemetry fields and explicit undefined have identical public link semantics.
        ...telemetry.sdkRegistered === void 0 ? {} : { sdkRegistered: telemetry.sdkRegistered },
        // Stryker disable next-line ConditionalExpression: omitted optional telemetry fields and explicit undefined have identical public link semantics.
        ...telemetry.remoteControllerConnected === void 0 ? {} : { remoteControllerConnected: telemetry.remoteControllerConnected },
        // Stryker disable next-line ConditionalExpression: omitted optional telemetry fields and explicit undefined have identical public link semantics.
        ...telemetry.flightControllerConnected === void 0 ? {} : { flightControllerConnected: telemetry.flightControllerConnected }
      });
    } catch {
      return freeze3({ field: "input", reason: "unreadable" });
    }
  }
  function evaluate3(value) {
    const input = readInput2(value);
    if (input === "invalid-container") return failure2("input", "invalid-container");
    if (input === "unreadable") return failure2("input", "unreadable");
    if (!validDeviceId2(input.deviceId)) return failure2("deviceId", "invalid-id");
    if (typeof input.relayConnected !== "boolean") return failure2("relayConnected", "invalid-type");
    const telemetry = readTelemetry(input.telemetry);
    if (telemetry !== null && "field" in telemetry) return failure2(telemetry.field, telemetry.reason);
    if (!input.relayConnected) return success(freeze3({ deviceId: input.deviceId, overall: "offline", computerToPhone: "disconnected", phoneToRemoteController: "unknown", remoteControllerToAircraft: "unknown" }));
    const sdkReady = telemetry !== null && (telemetry.sdkAvailability === void 0 ? telemetry.sdkRegistered === true : telemetry.sdkAvailability === "READY");
    if (telemetry === null || !sdkReady) return success(freeze3({ deviceId: input.deviceId, overall: "degraded", computerToPhone: "connected", phoneToRemoteController: "unknown", remoteControllerToAircraft: "unknown" }));
    const remoteController = telemetry.remoteController === "CONNECTED" || telemetry.remoteController === void 0 && telemetry.remoteControllerConnected === true ? "connected" : telemetry.remoteController === "DISCONNECTED" || telemetry.remoteController === void 0 && telemetry.remoteControllerConnected === false ? "disconnected" : "unknown";
    const aircraft = remoteController === "connected" ? telemetry.flightController === "CONNECTED" || telemetry.flightController === void 0 && telemetry.flightControllerConnected === true ? "connected" : telemetry.flightController === "DISCONNECTED" || telemetry.flightController === void 0 && telemetry.flightControllerConnected === false ? "disconnected" : "unknown" : "unknown";
    return success(freeze3({ deviceId: input.deviceId, overall: aircraft === "connected" ? "ready" : "degraded", computerToPhone: "connected", phoneToRemoteController: remoteController, remoteControllerToAircraft: aircraft }));
  }
  var LinkChain = Object.freeze({ evaluate: evaluate3 });

  // src/production/operator-console/index.ts
  var freeze4 = (value) => Object.freeze(value);
  var record = (value) => value !== null && typeof value === "object" && !Array.isArray(value) ? value : null;
  var read = (value, key) => {
    try {
      return record(value)?.[key];
    } catch {
      return void 0;
    }
  };
  var text = (value) => typeof value === "string" && value.trim().length > 0 ? value : null;
  var finite = (value) => typeof value === "number" && Number.isFinite(value) ? value : null;
  var devicesOf = (snapshot2) => {
    const workflow = read(snapshot2, "workflow");
    const devices = read(workflow, "devices");
    return Array.isArray(devices) ? devices.flatMap((item) => {
      const row = record(item);
      return row === null || !text(read(row, "deviceId")) ? [] : [row];
    }) : [];
  };
  var stillOnline = (devices, deviceId) => deviceId !== null && devices.some((item) => read(item, "deviceId") === deviceId) ? deviceId : null;
  var resolveSelection = (devices, selected) => {
    const current = stillOnline(devices, selected);
    if (current !== null) return current;
    if (selected !== null) return null;
    return devices.length === 1 ? text(read(devices[0], "deviceId")) : null;
  };
  var poseMarker = (device, role) => {
    const pose = read(read(device, "connection"), "pose");
    const latitude = finite(read(pose, "latitude"));
    const longitude = finite(read(pose, "longitude"));
    if (latitude === null || longitude === null) return null;
    return freeze4({ deviceId: text(read(device, "deviceId")), latitude, longitude, altitudeMeters: finite(read(pose, "altitudeMeters")), role });
  };
  var confirmationOf = (device) => {
    if (device === void 0) return null;
    const pending = record(read(device, "pendingFlightAction"));
    const deviceId = text(read(pending, "deviceId"));
    const action = text(read(pending, "action"));
    const confirmationId = text(read(pending, "confirmationId"));
    const expiresAtMs = finite(read(pending, "expiresAtMs"));
    return deviceId !== null && action !== null && confirmationId !== null && expiresAtMs !== null ? freeze4({ deviceId, action, confirmationId, expiresAtMs }) : null;
  };
  var telemetryBits = (connection) => freeze4({
    ...read(connection, "msdk") === "ready" ? { sdkAvailability: "READY" } : read(connection, "msdk") === "starting" ? { sdkAvailability: "STARTING" } : read(connection, "msdk") === "stopped" ? { sdkAvailability: "STOPPED" } : read(connection, "msdk") === "failed" ? { sdkAvailability: "FAILED" } : {},
    ...read(connection, "remoteController") === "connected" ? { remoteController: "CONNECTED" } : read(connection, "remoteController") === "disconnected" ? { remoteController: "DISCONNECTED" } : {},
    ...read(connection, "flightController") === "connected" ? { flightController: "CONNECTED" } : read(connection, "flightController") === "disconnected" ? { flightController: "DISCONNECTED" } : {}
  });
  var controlConnection = (device) => {
    if (device === void 0) return null;
    return record(read(device, "control")) ?? read(device, "connection");
  };
  var invocationConnection = (device) => {
    if (device === void 0) return null;
    const connection = record(read(device, "connection"));
    return connection !== null && read(connection, "msdk") !== void 0 ? connection : controlConnection(device);
  };
  var guidanceOf = (device) => {
    if (device === void 0) return null;
    const deviceId = text(read(device, "deviceId"));
    if (deviceId === null) return null;
    const connection = controlConnection(device);
    const link = LinkChain.evaluate({ deviceId, relayConnected: read(connection, "relay") === "online", telemetry: telemetryBits(connection) });
    if (!link.ok) return null;
    const guidance = DeviceGuidance.evaluate({ link: link.value });
    return guidance.ok ? guidance.value : null;
  };
  var markerRole = (deviceId, missionDeviceId, streamDeviceId, playingVideoDeviceId) => {
    const mission = deviceId === missionDeviceId;
    const stream = deviceId === streamDeviceId || deviceId === playingVideoDeviceId;
    return mission && stream ? "both" : mission ? "mission" : stream ? "stream" : "none";
  };
  var workspaceOf = (value) => value === "routes" || value === "flight" ? value : "devices";
  var routeFact = (value) => {
    const row = record(value);
    const routeId = text(read(row, "routeId"));
    if (routeId === null) return null;
    const format = text(read(row, "format"));
    const classification = text(read(row, "classification"));
    const previewOnly = format === "kml" || classification === "preview-only";
    const executable = classification === "upload-candidate";
    const blockedReason = previewOnly ? format === "kml" ? "KML \u53EA\u80FD\u9884\u89C8\uFF0C\u4E0D\u80FD\u63D0\u4EA4\u7ED9\u98DE\u673A" : "\u8BE5 KMZ \u672A\u901A\u8FC7\u4E0E\u624B\u673A\u7AEF\u4E00\u81F4\u7684\u63D0\u4EA4\u68C0\u67E5\uFF08\u9700 wpmz/waylines.wpml\u3001\u914D\u5957\u6A21\u677F\u3001\u6070\u597D\u4E00\u6761\u822A\u7EBF\uFF09\uFF0C\u4EC5\u53EF\u9884\u89C8" : executable ? null : "\u5C1A\u672A\u53D6\u5F97\u8BE5\u822A\u7EBF\u7684\u53EF\u6267\u884C\u6027\u4E8B\u5B9E";
    return freeze4({
      routeId,
      displayName: text(read(row, "displayName")) ?? routeId,
      format,
      classification,
      executable,
      previewable: true,
      blockedReason
    });
  };
  var routesOf = (snapshot2) => {
    const routes = read(read(snapshot2, "workflow"), "routes");
    return freeze4(Array.isArray(routes) ? routes.flatMap((item) => {
      const fact = routeFact(item);
      return fact === null ? [] : [fact];
    }) : []);
  };
  var missionRouteOf = (device, mission, routes) => {
    const routeId = text(read(mission, "routeId"));
    if (routeId === null) return null;
    const known = routes.find((route) => route.routeId === routeId);
    if (known !== void 0) return freeze4({ routeId, displayName: known.displayName });
    const assignment = read(device, "assignment");
    return freeze4({
      routeId,
      displayName: read(assignment, "routeId") === routeId ? text(read(assignment, "routeName")) ?? routeId : routeId
    });
  };
  var missionFailureLabel = (value) => {
    switch (text(value)) {
      case "MISSION_TRANSFER_FAILED":
        return "\u51C6\u5907\u822A\u7EBF\u5931\u8D25\uFF1A\u624B\u673A\u672A\u786E\u8BA4\u6587\u4EF6\u5DF2\u6821\u9A8C\u4FDD\u5B58";
      case "WAYLINE_UPLOAD_FAILED":
        return "\u4E0A\u4F20\u81F3\u98DE\u673A\u5931\u8D25\uFF1A\u98DE\u673A\u672A\u786E\u8BA4\u822A\u7EBF";
      case "WAYLINE_START_UNCONFIRMED":
        return "\u542F\u52A8\u72B6\u6001\u4E0D\u786E\u5B9A\uFF1A\u4E0D\u5F97\u91CD\u590D\u6267\u884C\uFF0C\u53EF\u505C\u6B62\u822A\u7EBF";
      case "WAYLINE_PAUSE_UNCONFIRMED":
        return "\u6682\u505C\u72B6\u6001\u4E0D\u786E\u5B9A\uFF1A\u4E0D\u5F97\u91CD\u590D\u6682\u505C\uFF0C\u53EF\u505C\u6B62\u822A\u7EBF";
      case "WAYLINE_RESUME_UNCONFIRMED":
        return "\u6062\u590D\u72B6\u6001\u4E0D\u786E\u5B9A\uFF1A\u4E0D\u5F97\u91CD\u590D\u6062\u590D\uFF0C\u53EF\u505C\u6B62\u822A\u7EBF";
      case "WAYLINE_STOP_UNCONFIRMED":
        return "\u505C\u6B62\u72B6\u6001\u4E0D\u786E\u5B9A\uFF1A\u6062\u590D\u624B\u673A\u548C MSDK \u8FDE\u63A5\u540E\u53EF\u518D\u6B21\u5C1D\u8BD5\u505C\u6B62";
      default:
        return "\u4EFB\u52A1\u5931\u8D25\uFF0C\u8BF7\u91CD\u65B0\u51C6\u5907\u822A\u7EBF";
    }
  };
  var missionLabelOf = (mission) => {
    switch (text(read(mission, "phase"))) {
      case "staging":
        return "\u6B63\u5728\u51C6\u5907\u822A\u7EBF\uFF08\u4F20\u8F93\u5E76\u6821\u9A8C\u4E2D\uFF09";
      case "staged":
        return "\u822A\u7EBF\u5DF2\u51C6\u5907\u5230\u624B\u673A\uFF08\u98DE\u673A\u5C1A\u672A\u6536\u5230\uFF09\u3002\u4E0B\u4E00\u6B65\uFF1A\u4E0A\u4F20\u81F3\u98DE\u673A";
      case "uploading":
        return "\u6B63\u5728\u4E0A\u4F20\u81F3\u98DE\u673A\uFF08\u7B49\u5F85\u624B\u673A\u786E\u8BA4\uFF09";
      case "uploaded":
        return "\u822A\u7EBF\u5DF2\u4E0A\u4F20\u81F3\u98DE\u673A\u3002\u4E0B\u4E00\u6B65\uFF1A\u6267\u884C\u822A\u7EBF";
      case "starting":
        return "\u542F\u52A8\u5DF2\u53D7\u7406\uFF0C\u7B49\u5F85\u98DE\u673A\u5B9E\u9645\u8FDB\u5165\u822A\u7EBF";
      case "running":
        return "\u6B63\u5728\u6267\u884C\u822A\u7EBF";
      case "pausing":
        return "\u6B63\u5728\u6682\u505C\uFF0C\u7B49\u5F85\u624B\u673A\u786E\u8BA4\uFF1B\u5982\u65E0\u54CD\u5E94\u53EF\u505C\u6B62\u822A\u7EBF";
      case "paused":
        return "\u5DF2\u6682\u505C";
      case "resuming":
        return "\u6B63\u5728\u6062\u590D\uFF0C\u7B49\u5F85\u624B\u673A\u786E\u8BA4\uFF1B\u5982\u65E0\u54CD\u5E94\u53EF\u505C\u6B62\u822A\u7EBF";
      case "stopping":
        return "\u505C\u6B62\u5DF2\u63D0\u4EA4\uFF0C\u7B49\u5F85\u624B\u673A\u786E\u8BA4\uFF1B\u7ED3\u679C\u672A\u786E\u8BA4\u4E14\u8FDE\u63A5\u6062\u590D\u540E\u53EF\u518D\u6B21\u5C1D\u8BD5\u505C\u6B62";
      case "completed":
        return "\u5DF2\u7ED3\u675F";
      case "failed":
        return missionFailureLabel(read(mission, "failureCode"));
      case "disconnected":
        return "\u4E0E\u624B\u673A\u5931\u8054\uFF0C\u98DE\u673A\u72B6\u6001\u672A\u77E5\uFF1B\u91CD\u8FDE\u540E\u53EA\u80FD\u505C\u6B62\u6216\u91CD\u65B0\u51C6\u5907\u822A\u7EBF";
      default:
        return "\u672A\u5F00\u59CB";
    }
  };
  var streamSourceUnavailableOf = (device) => text(read(read(device, "stream"), "phase")) === "failed" && text(read(read(device, "stream"), "failureCode")) === "SOURCE_UNAVAILABLE";
  var flightControllerVideoHintOf = (device) => {
    const connection = read(device, "connection");
    const live = read(connection, "live");
    const hint = FlightControllerVideoHint.evaluate({
      hasConnectedOnce: read(connection, "flightControllerHasConnectedOnce"),
      streaming: read(live, "streaming"),
      fps: read(live, "fps")
    });
    return freeze4({
      flightControllerVideoBanner: hint.banner,
      flightControllerVideoAfterStart: hint.afterStart
    });
  };
  var streamRuntimeErrorOf = (device) => {
    const error = read(read(read(device, "connection"), "live"), "runtimeError");
    const code = text(read(error, "code"));
    const description = text(read(error, "description"));
    return code === null || description === null ? null : freeze4({ code, description });
  };
  var streamLabelOf = (device) => {
    if (device === void 0) return "\u56FE\u4F20\u672A\u5C31\u7EEA\uFF1A\u672A\u9009\u62E9\u56FE\u4F20\u673A";
    const streamPhase = text(read(read(device, "stream"), "phase"));
    if (streamPhase === "stopping") return "\u6B63\u5728\u505C\u6B62\u56FE\u4F20";
    if (streamSourceUnavailableOf(device)) return "\u56FE\u4F20\u6E90\u5DF2\u65AD\u5F00\uFF0C\u8BF7\u6062\u590D\u540E\u624B\u52A8\u542F\u52A8\u56FE\u4F20";
    const runtimeError = streamRuntimeErrorOf(device);
    if (runtimeError !== null) return `DJI MSDK \u56FE\u4F20\u8FD0\u884C\u56DE\u8C03\uFF1A\u9519\u8BEF\u7801\uFF1A${runtimeError.code}\uFF1B\u9519\u8BEF\u8BF4\u660E\uFF1A${runtimeError.description}`;
    const videoPhase = text(read(read(device, "video"), "phase"));
    if (videoPhase === "ready") return "\u56FE\u4F20\u64AD\u653E\u4E2D";
    if (videoPhase === "awaiting-playback") return "\u6B63\u5728\u51C6\u5907\u753B\u9762";
    if (videoPhase === "awaiting-ingest") return "\u624B\u673A\u5DF2\u63A5\u53D7\u63A8\u6D41\uFF0C\u7B49\u5F85\u63A5\u6536";
    if (videoPhase === "failed") return "\u56FE\u4F20\u5931\u8D25";
    if (streamPhase === "starting" || streamPhase === "streaming") {
      return "\u624B\u673A\u5DF2\u63A5\u547D\u4EE4\uFF0C\u7535\u8111\u8FD8\u6CA1\u6536\u5230\u753B\u9762";
    }
    if (streamPhase === "stopping") return "\u6B63\u5728\u505C\u6B62\u56FE\u4F20";
    if (streamPhase === "failed") return "\u56FE\u4F20\u5931\u8D25";
    if (streamPhase === "disconnected") return "\u56FE\u4F20\u5DF2\u4E2D\u65AD\uFF0C\u53EF\u91CD\u65B0\u542F\u52A8";
    const issue2 = streamStartIssueOf(device);
    if (issue2 !== null) return `\u56FE\u4F20\u672A\u5C31\u7EEA\uFF1A${issue2.label}`;
    const sourceState = text(read(read(device, "capabilities"), "liveVideo"));
    if (sourceState === "unsupported") return "\u56FE\u4F20\u53EF\u5C1D\u8BD5\u542F\u52A8\uFF08\u56FE\u4F20\u6E90\u5F53\u524D\u62A5\u544A\u672A\u5C31\u7EEA\uFF09";
    if (sourceState !== "supported") return "\u56FE\u4F20\u53EF\u5C1D\u8BD5\u542F\u52A8\uFF08\u56FE\u4F20\u6E90\u72B6\u6001\u672A\u77E5\uFF09";
    return "\u56FE\u4F20\u53EF\u8BF7\u6C42\u542F\u52A8";
  };
  var streamStartIssueOf = (device) => {
    if (device === void 0) return freeze4({ label: "\u672A\u9009\u62E9\u56FE\u4F20\u673A", reason: "\u8BF7\u9009\u62E9\u7528\u4E8E\u56FE\u4F20\u7684\u98DE\u673A" });
    const connection = invocationConnection(device);
    const msdk = read(connection, "msdk");
    const sdkReady = msdk === void 0 ? read(connection, "sdk") === "ready" : msdk === "ready";
    if (!sdkReady) return freeze4({ label: "\u7B49\u5F85\u624B\u673A\u5C31\u7EEA", reason: "\u624B\u673A\u5C1A\u672A\u5C31\u7EEA\uFF0C\u65E0\u6CD5\u542F\u52A8\u56FE\u4F20" });
    const airLink = read(connection, "airLink");
    if (airLink !== "connected") return freeze4({
      label: airLink === "disconnected" ? "AirLink \u672A\u8FDE\u63A5" : "AirLink \u72B6\u6001\u672A\u77E5",
      reason: airLink === "disconnected" ? "AirLink \u672A\u8FDE\u63A5\uFF0C\u65E0\u6CD5\u542F\u52A8\u56FE\u4F20" : "AirLink \u72B6\u6001\u672A\u77E5\uFF0C\u65E0\u6CD5\u542F\u52A8\u56FE\u4F20"
    });
    const camera = read(connection, "camera");
    if (camera !== "connected") return freeze4({
      label: camera === "disconnected" ? "\u4E3B\u76F8\u673A\u672A\u8FDE\u63A5" : "\u4E3B\u76F8\u673A\u72B6\u6001\u672A\u77E5",
      reason: camera === "disconnected" ? "\u4E3B\u76F8\u673A\u672A\u8FDE\u63A5\uFF0C\u65E0\u6CD5\u542F\u52A8\u56FE\u4F20" : "\u4E3B\u76F8\u673A\u72B6\u6001\u672A\u77E5\uFF0C\u65E0\u6CD5\u542F\u52A8\u56FE\u4F20"
    });
    return null;
  };
  var phoneStillStreaming = (device) => read(read(read(device, "connection"), "live"), "streaming") === true;
  var streamCanStartOf = (device) => {
    if (device === void 0) return false;
    const streamPhase = text(read(read(device, "stream"), "phase"));
    if (streamPhase === "starting" || streamPhase === "streaming" || phoneStillStreaming(device)) return false;
    return streamStartIssueOf(device) === null;
  };
  var streamCanStopOf = (device) => {
    if (device === void 0) return false;
    if (streamSourceUnavailableOf(device)) return false;
    if (phoneStillStreaming(device)) return true;
    const streamPhase = text(read(read(device, "stream"), "phase"));
    if (streamPhase === "starting" || streamPhase === "streaming" || streamPhase === "stopping" || streamPhase === "failed") return true;
    const videoPhase = text(read(read(device, "video"), "phase"));
    return videoPhase === "ready" || videoPhase === "awaiting-playback" || videoPhase === "awaiting-ingest";
  };
  var photoIssueOf = (device) => {
    const connection = invocationConnection(device);
    const msdk = read(connection, "msdk");
    const sdkReady = msdk === void 0 ? read(connection, "sdk") === "ready" : msdk === "ready";
    if (!sdkReady) return "\u624B\u673A\u5C1A\u672A\u5C31\u7EEA\uFF0C\u65E0\u6CD5\u62CD\u7167";
    const camera = read(connection, "camera");
    if (camera === "disconnected") return "\u4E3B\u76F8\u673A\u672A\u8FDE\u63A5\uFF0C\u65E0\u6CD5\u62CD\u7167";
    if (camera !== "connected") return "\u4E3B\u76F8\u673A\u72B6\u6001\u672A\u77E5\uFF0C\u65E0\u6CD5\u62CD\u7167";
    return null;
  };
  var reject = (reason) => freeze4({ ok: false, reason });
  var accept = () => freeze4({ ok: true });
  var msdkInvocationIssue = (device) => {
    const connection = invocationConnection(device);
    const msdk = read(connection, "msdk");
    if (msdk !== void 0) return msdk === "ready" ? null : "\u624B\u673A\u5C1A\u672A\u5C31\u7EEA";
    return read(connection, "sdk") === "ready" ? null : "\u624B\u673A\u5C1A\u672A\u5C31\u7EEA";
  };
  var deviceById = (view, deviceId) => view.devices.flatMap((item) => {
    const row = record(item);
    return row !== null && read(row, "deviceId") === deviceId ? [row] : [];
  })[0];
  var flightDevice = (view, action) => {
    if (view.workspace === "devices") return reject("\u8BF7\u5230\u98DE\u884C\u9875\u6267\u884C\u4EFB\u52A1");
    if (view.workspace === "routes") return reject("\u822A\u7EBF\u9875\u4E0D\u6267\u884C\u98DE\u884C\u6216\u56FE\u4F20\uFF0C\u8BF7\u5230\u98DE\u884C\u9875\u64CD\u4F5C");
    const streamAction = action.startsWith("stream-") || action.startsWith("photo-");
    const deviceId = streamAction ? view.streamDeviceId : view.missionDeviceId;
    if (deviceId === null) return reject(streamAction ? "\u8BF7\u9009\u62E9\u7528\u4E8E\u56FE\u4F20\u7684\u98DE\u673A" : "\u8BF7\u9009\u62E9\u7528\u4E8E\u6267\u884C\u4EFB\u52A1\u7684\u98DE\u673A");
    const device = deviceById(view, deviceId);
    if (device === void 0) return reject("\u6240\u9009\u624B\u673A\u5DF2\u79BB\u7EBF");
    return device;
  };
  var rejected = (value) => "ok" in value;
  var lastResultOf = (mission) => {
    const last = record(read(mission, "lastResult"));
    return freeze4({
      operation: text(read(last, "operation")),
      ok: last === null ? null : read(last, "ok") === true,
      code: text(read(last, "code"))
    });
  };
  var missionOperationLabel = (operation) => {
    if (operation === "stage") return "\u51C6\u5907\u822A\u7EBF";
    if (operation === "upload") return "\u4E0A\u4F20\u81F3\u98DE\u673A";
    if (operation === "start") return "\u6267\u884C\u822A\u7EBF";
    if (operation === "pause") return "\u6682\u505C\u822A\u7EBF";
    if (operation === "resume") return "\u6062\u590D\u822A\u7EBF";
    if (operation === "stop") return "\u505C\u6B62\u822A\u7EBF";
    return "\u822A\u7EBF\u64CD\u4F5C";
  };
  var missionCommandOf = (mission, phase) => {
    const last = lastResultOf(mission);
    if (last.code !== null && last.code.endsWith("_UNCONFIRMED")) return `${missionOperationLabel(last.operation)}\u7ED3\u679C\u672A\u786E\u8BA4\uFF1A\u4E0D\u80FD\u5224\u65AD\u98DE\u673A\u662F\u5426\u5DF2\u6267\u884C`;
    if (last.ok === false && last.code === "WAYLINE_ACTION_REJECTED") return `${missionOperationLabel(last.operation)}\u88AB DJI \u660E\u786E\u62D2\u7EDD`;
    if (last.ok === false && last.code === "PREFLIGHT_BLOCKED") return `${missionOperationLabel(last.operation)}\u672A\u53D1\u51FA\uFF1A\u624B\u673A\u6216 MSDK \u4E0D\u53EF\u8FBE`;
    if (phase === "staging") return "\u6B63\u5728\u628A\u822A\u7EBF\u4F20\u5230\u624B\u673A\u5E76\u6821\u9A8C";
    if (phase === "uploading") return "\u6B63\u5728\u628A\u822A\u7EBF\u4E0A\u4F20\u5230\u98DE\u673A\uFF0C\u7B49\u5F85 DJI \u786E\u8BA4";
    if (phase === "starting") return "DJI \u5DF2\u63A5\u53D7\u6267\u884C\u822A\u7EBF\uFF0C\u4E0D\u7B49\u4E8E\u98DE\u673A\u5DF2\u8FDB\u5165\u822A\u7EBF";
    if (phase === "pausing") return "\u6682\u505C\u547D\u4EE4\u5DF2\u53D1\u51FA\uFF0C\u7B49\u5F85 DJI \u786E\u8BA4";
    if (phase === "resuming") return "\u6062\u590D\u547D\u4EE4\u5DF2\u53D1\u51FA\uFF0C\u7B49\u5F85 DJI \u786E\u8BA4";
    if (phase === "stopping") return "\u505C\u6B62\u547D\u4EE4\u5DF2\u53D1\u51FA\uFF0C\u7B49\u5F85 DJI \u786E\u8BA4";
    if (last.ok === true) return `${missionOperationLabel(last.operation)}\u8C03\u7528\u5DF2\u5B8C\u6210`;
    return "\u5F53\u524D\u6CA1\u6709\u8FDB\u884C\u4E2D\u7684\u822A\u7EBF\u547D\u4EE4";
  };
  var missionEffectOf = (mission, phase) => {
    if (read(mission, "routeExecutionStarted") === true || phase === "running") return "\u98DE\u673A\u6B63\u5728\u6267\u884C\u822A\u7EBF";
    if (read(mission, "startPointReached") === true) return "\u98DE\u673A\u5DF2\u8FDB\u5165\u9996\u822A\u70B9\uFF0C\u5C1A\u672A\u786E\u8BA4\u5F00\u59CB\u6267\u884C\u822A\u7EBF";
    if (phase === "starting") return "\u5C1A\u672A\u6536\u5230\u5F53\u524D\u4EFB\u52A1\u7684\u822A\u7EBF\u5B9E\u9645\u5F00\u59CB\u6267\u884C";
    if (phase === "paused") return "\u822A\u7EBF\u5DF2\u6682\u505C";
    if (phase === "uploaded") return "\u98DE\u673A\u5DF2\u6536\u5230\u822A\u7EBF\uFF0C\u5C1A\u672A\u6267\u884C";
    if (phase === "staged") return "\u822A\u7EBF\u53EA\u5728\u624B\u673A\u4E0A\uFF0C\u98DE\u673A\u5C1A\u672A\u6536\u5230";
    if (phase === "completed") return "\u822A\u7EBF\u5DF2\u7ED3\u675F";
    if (phase === "failed") return "\u4EFB\u52A1\u5931\u8D25\uFF0C\u98DE\u673A\u6548\u679C\u4EE5\u9065\u63A7\u5668\u548C\u98DE\u673A\u4E3A\u51C6";
    if (phase === "disconnected") return "\u4E0E\u624B\u673A\u5931\u8054\uFF0C\u98DE\u673A\u72B6\u6001\u672A\u77E5";
    if (phase === "idle" || phase === null) return "\u6CA1\u6709\u5F53\u524D\u4EFB\u52A1\u6548\u679C";
    return "\u7B49\u5F85\u624B\u673A\u786E\u8BA4\u6B64\u6B21\u547D\u4EE4\u7684\u8BBE\u5907\u6548\u679C";
  };
  var missionNextOf = (actions, phase, unconfirmedOperation) => {
    if (unconfirmedOperation === "start") return "\u53EF\u518D\u70B9\u6267\u884C\u3002\u4E5F\u53EF\u505C\u6B62\u822A\u7EBF";
    if (unconfirmedOperation === "pause" || unconfirmedOperation === "resume") return "\u4E0D\u5F97\u91CD\u590D\u540C\u4E00\u547D\u4EE4\u3002\u53EF\u518D\u70B9\u6267\u884C\uFF0C\u4E5F\u53EF\u505C\u6B62\u822A\u7EBF";
    if (unconfirmedOperation === "stop") return "\u505C\u6B62\u7ED3\u679C\u672A\u786E\u8BA4\u3002\u6062\u590D\u624B\u673A\u548C MSDK \u540E\u53EF\u518D\u6B21\u5C1D\u8BD5\u505C\u6B62";
    if (phase === "starting") return "\u7B49\u5F85\u8FDB\u5165\u822A\u7EBF\u3002\u53EF\u505C\u6B62\uFF0C\u4E5F\u53EF\u518D\u70B9\u6267\u884C";
    if (actions.upload.enabled) return "\u4E0B\u4E00\u6B65\uFF1A\u70B9\u300C\u4E0A\u4F20\u81F3\u98DE\u673A\u300D";
    if (actions.start.enabled) return "\u4E0B\u4E00\u6B65\uFF1A\u70B9\u300C\u6267\u884C\u822A\u7EBF\u300D";
    if (actions.pause.enabled) return "\u4E0B\u4E00\u6B65\uFF1A\u53EF\u6682\u505C\u6216\u505C\u6B62\u822A\u7EBF";
    if (actions.resume.enabled) return "\u4E0B\u4E00\u6B65\uFF1A\u53EF\u6062\u590D\u6216\u505C\u6B62\u822A\u7EBF";
    if (actions.stage.enabled) return "\u4E0B\u4E00\u6B65\uFF1A\u70B9\u300C\u51C6\u5907\u822A\u7EBF\u300D";
    if (actions.stop.enabled) return "\u73B0\u5728\u53EA\u80FD\u505C\u6B62\u822A\u7EBF";
    return "\u5F53\u524D\u6CA1\u6709\u53EF\u6267\u884C\u7684\u822A\u7EBF\u547D\u4EE4";
  };
  var missionProgressOf = (mission, deviceId, actions, headline) => {
    if (deviceId === null) return freeze4({ headline: "\u672A\u9009\u62E9\u4EFB\u52A1\u673A", command: "\u6CA1\u6709\u53EF\u53D1\u9001\u7684\u822A\u7EBF\u547D\u4EE4", effect: "\u6CA1\u6709\u5F53\u524D\u4EFB\u52A1", next: "\u8BF7\u9009\u62E9\u4EFB\u52A1\u624B\u673A" });
    const phase = text(read(mission, "phase"));
    const last = lastResultOf(mission);
    const unconfirmed2 = last.code !== null && last.code.endsWith("_UNCONFIRMED") ? last.operation : null;
    return freeze4({ headline, command: missionCommandOf(mission, phase), effect: missionEffectOf(mission, phase), next: missionNextOf(actions, phase, unconfirmed2) });
  };
  var streamCommandOf = (device, playbackReady, sourceUnavailable, headline) => {
    const phase = text(read(read(device, "stream"), "phase"));
    const failure3 = text(read(read(device, "stream"), "failureCode"));
    if (sourceUnavailable) return "\u56FE\u4F20\u6E90\u5DF2\u65AD\u5F00\uFF0C\u624B\u673A\u5DF2\u6392\u961F\u6062\u590D\u6027\u505C\u6B62";
    if (failure3 !== null && failure3.endsWith("_UNCONFIRMED")) return "\u56FE\u4F20\u547D\u4EE4\u7ED3\u679C\u672A\u786E\u8BA4\uFF1A\u4E0D\u80FD\u5224\u65AD\u624B\u673A\u662F\u5426\u4ECD\u5728\u63A8\u6D41";
    if (phase === "stopping") return "\u505C\u6B62\u56FE\u4F20\u5DF2\u53D1\u51FA\uFF0C\u7B49\u5F85\u624B\u673A\u786E\u8BA4";
    if (phase === "starting" || phase === "streaming") return playbackReady ? "DJI \u5DF2\u63A5\u53D7\u542F\u52A8\u56FE\u4F20" : "DJI \u5DF2\u63A5\u53D7\u542F\u52A8\u56FE\u4F20\uFF0C\u4E0D\u7B49\u4E8E\u7535\u8111\u5DF2\u6536\u5230\u753B\u9762";
    if (headline.startsWith("DJI MSDK \u56FE\u4F20\u8FD0\u884C\u56DE\u8C03\uFF1A")) return "\u542F\u52A8\u540E\u7684\u8FD0\u884C\u56DE\u8C03\u62A5\u9519\uFF0C\u4E0D\u662F\u672C\u6B21\u6309\u94AE\u7684\u5B8C\u6210\u56DE\u6267";
    if (phase === "failed") return "\u56FE\u4F20\u547D\u4EE4\u5931\u8D25";
    return "\u5F53\u524D\u6CA1\u6709\u8FDB\u884C\u4E2D\u7684\u56FE\u4F20\u547D\u4EE4";
  };
  var streamEffectOf = (playbackReady, device, sourceUnavailable) => {
    const videoPhase = text(read(read(device, "video"), "phase"));
    if (sourceUnavailable) return "\u672C\u5730\u753B\u9762\u5DF2\u5931\u6548\uFF0C\u9700\u8981\u6062\u590D\u540E\u624B\u52A8\u518D\u542F";
    if (playbackReady) return "\u7535\u8111\u6B63\u5728\u64AD\u653E\u753B\u9762";
    if (videoPhase === "awaiting-playback") return "\u7535\u8111\u5DF2\u6536\u5230\u53EF\u64AD\u653E\u5730\u5740\uFF0C\u753B\u9762\u5C1A\u672A\u6302\u4E0A";
    if (videoPhase === "awaiting-ingest") return "\u624B\u673A\u5DF2\u63A5\u53D7\u63A8\u6D41\uFF0C\u7535\u8111\u8FD8\u5728\u7B49 RTMP";
    const phase = text(read(read(device, "stream"), "phase"));
    if (phase === "starting" || phase === "streaming") return "\u7535\u8111\u8FD8\u6CA1\u6709\u53EF\u64AD\u653E\u753B\u9762";
    if (phase === "stopping") return "\u505C\u6B62\u5C1A\u672A\u786E\u8BA4\uFF0C\u6700\u540E\u4E00\u5E27\u4E0D\u80FD\u5F53\u6210\u4ECD\u5728\u56FE\u4F20";
    return "\u6CA1\u6709\u53EF\u64AD\u653E\u753B\u9762";
  };
  var streamNextOf = (canStart, canStop, sourceUnavailable, phase, playbackReady) => {
    if (sourceUnavailable) return "\u6062\u590D AirLink \u548C\u4E3B\u76F8\u673A\u540E\uFF0C\u518D\u70B9\u300C\u542F\u52A8\u56FE\u4F20\u300D";
    if (phase === "stopping") return canStart ? "\u7B49\u5F85\u505C\u6B62\u786E\u8BA4\u3002\u786E\u8BA4\u540E\u53EF\u70B9\u300C\u505C\u6B62\u540E\u91CD\u542F\u56FE\u4F20\u300D" : "\u7B49\u5F85\u505C\u6B62\u786E\u8BA4\u3002\u5B8C\u6210\u540E\u624D\u80FD\u91CD\u65B0\u542F\u52A8";
    if (canStop || playbackReady) return "\u8981\u7ED3\u675F\u8BF7\u70B9\u300C\u505C\u6B62\u56FE\u4F20\u300D";
    if (canStart) return "\u4E0B\u4E00\u6B65\uFF1A\u70B9\u300C\u542F\u52A8\u56FE\u4F20\u300D";
    return "\u73B0\u5728\u4E0D\u80FD\u542F\u52A8\u56FE\u4F20";
  };
  var streamProgressOf = (device, headline, canStart, canStop, playbackReady, sourceUnavailable) => {
    if (device === void 0) return freeze4({ headline: "\u56FE\u4F20\u672A\u5C31\u7EEA\uFF1A\u672A\u9009\u62E9\u56FE\u4F20\u673A", command: "\u6CA1\u6709\u53EF\u53D1\u9001\u7684\u56FE\u4F20\u547D\u4EE4", effect: "\u6CA1\u6709\u53EF\u64AD\u653E\u753B\u9762", next: "\u8BF7\u9009\u62E9\u56FE\u4F20\u624B\u673A" });
    const phase = text(read(read(device, "stream"), "phase"));
    return freeze4({
      headline,
      command: streamCommandOf(device, playbackReady, sourceUnavailable, headline),
      effect: streamEffectOf(playbackReady, device, sourceUnavailable),
      next: streamNextOf(canStart, canStop, sourceUnavailable, phase, playbackReady)
    });
  };
  var flightActionName = (action) => {
    if (action === "takeoff") return "\u8D77\u98DE";
    if (action === "land") return "\u964D\u843D";
    if (action === "confirm-landing") return "\u786E\u8BA4\u7EE7\u7EED\u964D\u843D";
    if (action === "return-home") return "\u8FD4\u822A";
    if (action === "stop-takeoff") return "\u505C\u6B62\u81EA\u52A8\u8D77\u98DE";
    if (action === "stop-auto-landing") return "\u505C\u6B62\u81EA\u52A8\u964D\u843D";
    if (action === "stop-go-home") return "\u9000\u51FA\u8FD4\u822A";
    return action ?? "\u98DE\u884C\u52A8\u4F5C";
  };
  var flightProgressOf = (device, confirmation) => {
    if (device === void 0) return freeze4({ headline: "\u672A\u9009\u62E9\u4EFB\u52A1\u673A", command: "\u6CA1\u6709\u53EF\u53D1\u9001\u7684\u98DE\u884C\u52A8\u4F5C", effect: "\u6CA1\u6709\u5F53\u524D\u98DE\u884C\u6548\u679C", next: "\u8BF7\u9009\u62E9\u4EFB\u52A1\u624B\u673A" });
    const landing = text(read(read(device, "landing"), "phase"));
    const flying = text(read(read(device, "connection"), "flightState"));
    const motorsOn = read(read(device, "connection"), "motorsOn");
    if (confirmation !== null) {
      const label = flightActionName(confirmation.action);
      return freeze4({
        headline: `\u7B49\u5F85\u4EBA\u5DE5\u786E\u8BA4\uFF1A${label} \u5C1A\u672A\u8C03\u7528 DJI`,
        command: `${label}\u5C1A\u672A\u8C03\u7528 DJI\uFF0C\u53EA\u751F\u6210\u4E86\u672C\u5730\u786E\u8BA4`,
        effect: "\u98DE\u673A\u72B6\u6001\u5C1A\u672A\u56E0\u8FD9\u6B21\u70B9\u51FB\u6539\u53D8",
        next: "\u770B\u786E\u8BA4\u6846\uFF1A\u786E\u8BA4\u540E\u624D\u4F1A\u4E0B\u53D1\uFF0C\u53D6\u6D88\u5219\u4E0D\u53D1\u9001"
      });
    }
    if (landing === "awaiting-msdk") {
      return freeze4({
        headline: "DJI \u5DF2\u63A5\u53D7\u964D\u843D\uFF0C\u7B49\u5F85\u843D\u5730\u786E\u8BA4",
        command: "DJI \u5DF2\u63A5\u53D7\u81EA\u52A8\u964D\u843D\uFF0C\u4E0D\u7B49\u4E8E\u5DF2\u7ECF\u843D\u5730",
        effect: "\u4ECD\u5728\u7B49\u5F85\u672A\u98DE\u884C\u4E14\u7535\u673A\u5173\u95ED",
        next: "\u6301\u7EED\u89C2\u5BDF\u964D\u843D\u8FC7\u7A0B\uFF1B\u9700\u8981\u65F6\u53EF\u505C\u6B62\u81EA\u52A8\u964D\u843D"
      });
    }
    if (landing === "confirmation-required") {
      return freeze4({
        headline: "DJI \u8981\u6C42\u786E\u8BA4\u7EE7\u7EED\u964D\u843D",
        command: "\u964D\u843D\u5DF2\u5728\u8FDB\u884C\uFF0CDJI \u6B63\u5728\u7B49\u786E\u8BA4\u7EE7\u7EED\u964D\u843D",
        effect: "\u5C1A\u672A\u786E\u8BA4\u843D\u5730",
        next: "\u4E0B\u4E00\u6B65\uFF1A\u786E\u8BA4\u7EE7\u7EED\u964D\u843D\uFF0C\u6216\u505C\u6B62\u81EA\u52A8\u964D\u843D"
      });
    }
    if (landing === "confirmed-grounded") {
      return freeze4({
        headline: "\u5DF2\u786E\u8BA4\u843D\u5730",
        command: "\u6700\u8FD1\u4E00\u6B21\u964D\u843D\u547D\u4EE4\u5DF2\u88AB DJI \u63A5\u53D7",
        effect: "MSDK \u6301\u7EED\u72B6\u6001\uFF1A\u672A\u98DE\u884C\u4E14\u7535\u673A\u5173\u95ED",
        next: "\u964D\u843D\u5DF2\u5B8C\u6210\uFF0C\u53EF\u8FDB\u884C\u4E0B\u4E00\u6B65\u4F5C\u4E1A"
      });
    }
    if (landing === "stopped") {
      return freeze4({
        headline: "\u81EA\u52A8\u964D\u843D\u5DF2\u505C\u6B62",
        command: "\u505C\u6B62\u81EA\u52A8\u964D\u843D\u5DF2\u63D0\u4EA4",
        effect: "\u8BF7\u4EE5\u9065\u63A7\u5668\u548C\u98DE\u884C\u72B6\u6001\u4E3A\u51C6",
        next: "\u6301\u7EED\u89C2\u5BDF\u98DE\u884C\u72B6\u6001\u540E\u518D\u51B3\u5B9A\u4E0B\u4E00\u6B65"
      });
    }
    if (landing === "state-unknown") {
      return freeze4({
        headline: "\u964D\u843D\u547D\u4EE4\u5DF2\u88AB\u63A5\u53D7\uFF0C\u4F46\u98DE\u884C\u72B6\u6001\u5F53\u524D\u672A\u77E5",
        command: "DJI \u5DF2\u63A5\u53D7\u964D\u843D",
        effect: "\u98DE\u63A7\u72B6\u6001\u4E0D\u8DB3\u4EE5\u786E\u8BA4\u843D\u5730",
        next: "\u4EE5\u9065\u63A7\u5668\u4E3A\u51C6\uFF0C\u5FC5\u8981\u65F6\u505C\u6B62\u81EA\u52A8\u964D\u843D"
      });
    }
    if (flying === "flying") {
      return freeze4({
        headline: "\u98DE\u673A\u5728\u7A7A\u4E2D\uFF0C\u5F53\u524D\u6CA1\u6709\u5F85\u786E\u8BA4\u7684\u76F4\u63A5\u98DE\u884C\u52A8\u4F5C",
        command: "\u5F53\u524D\u6CA1\u6709\u8FDB\u884C\u4E2D\u7684\u76F4\u63A5\u98DE\u884C\u547D\u4EE4",
        effect: motorsOn === false ? "MSDK \u62A5\u544A\u5728\u98DE\uFF0C\u7535\u673A\u72B6\u6001\u672A\u542F\u52A8" : "MSDK \u62A5\u544A\u98DE\u673A\u5728\u98DE",
        next: "\u53EF\u8BF7\u6C42\u964D\u843D\u3001\u8FD4\u822A\u6216\u505C\u6B62\u7C7B\u52A8\u4F5C"
      });
    }
    return freeze4({
      headline: "\u5F53\u524D\u6CA1\u6709\u8FDB\u884C\u4E2D\u7684\u76F4\u63A5\u98DE\u884C\u52A8\u4F5C",
      command: "\u5F53\u524D\u6CA1\u6709\u8FDB\u884C\u4E2D\u7684\u76F4\u63A5\u98DE\u884C\u547D\u4EE4",
      effect: flying === "grounded" ? "MSDK \u62A5\u544A\u5728\u5730\u9762" : "\u6CA1\u6709\u5DF2\u786E\u8BA4\u7684\u76F4\u63A5\u98DE\u884C\u6548\u679C",
      next: "\u53EF\u8BF7\u6C42\u8D77\u98DE\u3001\u964D\u843D\u6216\u8FD4\u822A"
    });
  };
  var missionActionState = (result) => freeze4({ enabled: result.ok, reason: result.ok ? null : result.reason ?? "\u5F53\u524D\u9636\u6BB5\u4E0D\u80FD\u6267\u884C\u6B64\u64CD\u4F5C" });
  var missionActionsOf = (view) => freeze4({
    stage: missionActionState(evaluate4("mission-stage", view)),
    upload: missionActionState(evaluate4("mission-upload", view)),
    start: missionActionState(evaluate4("mission-start", view)),
    pause: missionActionState(evaluate4("mission-pause", view)),
    resume: missionActionState(evaluate4("mission-resume", view)),
    stop: missionActionState(evaluate4("mission-stop", view))
  });
  function project(input) {
    const source = record(input);
    const snapshot2 = source === null ? null : read(source, "snapshot");
    const selection = record(source === null ? null : read(source, "selection"));
    const devices = devicesOf(snapshot2);
    const missionDeviceId = resolveSelection(devices, text(read(selection, "missionDeviceId")));
    const streamDeviceId = resolveSelection(devices, text(read(selection, "streamDeviceId")));
    const playingVideoDeviceId = stillOnline(devices, text(read(read(snapshot2, "workflow"), "selectedVideoDeviceId")));
    const missionDevice = devices.find((item) => read(item, "deviceId") === missionDeviceId);
    const streamDevice = devices.find((item) => read(item, "deviceId") === streamDeviceId);
    const routes = routesOf(snapshot2);
    const selectedRouteId = text(read(read(snapshot2, "workflow"), "selectedRouteId"));
    const selectedRoute = routes.find((item) => item.routeId === selectedRouteId) ?? routes[0] ?? null;
    const videoPhase = text(read(read(streamDevice, "video"), "phase"));
    const streamSourceUnavailable = streamSourceUnavailableOf(streamDevice);
    const mission = missionDevice === void 0 ? null : read(missionDevice, "mission");
    const view = {
      workspace: workspaceOf(source === null ? null : read(source, "workspace")),
      relayHint: text(source === null ? null : read(source, "relayHint")) ?? "ws://<\u7535\u8111IPv4>:8080/relay",
      devices: freeze4(devices.map((item) => freeze4({ ...item }))),
      missionDeviceId,
      streamDeviceId,
      playingVideoDeviceId,
      markers: freeze4(devices.flatMap((item) => {
        const marker = poseMarker(item, markerRole(text(read(item, "deviceId")), missionDeviceId, streamDeviceId, playingVideoDeviceId));
        return marker === null ? [] : [marker];
      })),
      confirmation: confirmationOf(missionDevice),
      mission,
      missionRoute: missionRouteOf(missionDevice, mission, routes),
      guidance: guidanceOf(missionDevice),
      routes,
      selectedRoute,
      missionLabel: missionLabelOf(missionDevice === void 0 ? null : read(missionDevice, "mission")),
      streamLabel: streamLabelOf(streamDevice),
      streamSourceUnavailable,
      playbackReady: videoPhase === "ready" && !streamSourceUnavailable,
      streamCanStart: streamCanStartOf(streamDevice),
      streamCanStop: streamCanStopOf(streamDevice),
      ...flightControllerVideoHintOf(streamDevice),
      media: read(read(snapshot2, "workflow"), "media")
    };
    const missionActions2 = missionActionsOf(view);
    const progress = freeze4({
      mission: missionProgressOf(mission, missionDeviceId, missionActions2, view.missionLabel),
      stream: streamProgressOf(streamDevice, view.streamLabel, view.streamCanStart, view.streamCanStop, view.playbackReady, streamSourceUnavailable),
      flight: flightProgressOf(missionDevice, view.confirmation)
    });
    return freeze4({ ...view, missionActions: missionActions2, progress });
  }
  function evaluate4(action, view) {
    const name = text(action);
    const current = record(view);
    if (name === null || current === null) return reject("\u64CD\u4F5C\u65E0\u6548");
    if (name === "pairing-start" || name === "pairing-stop") {
      if (current.workspace !== "devices") return reject("\u8BF7\u5230\u8BBE\u5907\u9875\u64CD\u4F5C\u5BF9\u9891");
      const device2 = deviceById(current, current.missionDeviceId);
      if (device2 === void 0) return reject("\u8BF7\u5148\u9009\u62E9\u5DF2\u8FDE\u63A5\u7684\u624B\u673A");
      const connection = read(device2, "connection");
      if (read(connection, "msdk") !== "ready") return reject("\u624B\u673A\u7AEF DJI \u5C1A\u672A\u5C31\u7EEA");
      const pairingState = read(connection, "pairingState");
      if (name === "pairing-stop") return pairingState === "PAIRING" || pairingState === "PAIRED" || pairingState === "STOPPING" ? accept() : reject("\u5F53\u524D\u6CA1\u6709\u53EF\u505C\u6B62\u7684\u5BF9\u9891");
      if (read(connection, "remoteController") !== "connected") return reject("\u9065\u63A7\u5668\u5C1A\u672A\u8FDE\u63A5");
      if (read(connection, "flightController") === "connected") return reject("\u98DE\u63A7\u5DF2\u8FDE\u63A5\uFF0C\u65E0\u9700\u5BF9\u9891");
      if (pairingState === "PAIRING") return reject("\u5BF9\u9891\u64CD\u4F5C\u6B63\u5728\u8FDB\u884C");
      return accept();
    }
    if (name === "import-route" || name === "select-route" || name === "remove-route") {
      return current.workspace === "routes" ? accept() : reject("\u8BF7\u5230\u822A\u7EBF\u9875\u5BFC\u5165\u6216\u7BA1\u7406\u822A\u7EBF");
    }
    const device = flightDevice(current, name);
    if (rejected(device)) return device;
    if (name === "stream-start" || name === "stream-stop" || name === "stream-select") {
      if (name === "stream-start") {
        const issue2 = streamStartIssueOf(device);
        if (issue2 !== null) return reject(issue2.reason);
      }
      if (name === "stream-stop" && streamSourceUnavailableOf(device)) return reject("\u56FE\u4F20\u6E90\u5DF2\u65AD\u5F00\uFF0C\u624B\u673A\u5DF2\u81EA\u52A8\u505C\u6B62\u56FE\u4F20");
      return accept();
    }
    if (name === "photo-capture" || name === "photo-fetch") {
      const issue2 = photoIssueOf(device);
      return issue2 === null ? accept() : reject(issue2);
    }
    if (name === "flight-confirm" || name === "flight-cancel") return accept();
    if (name === "flight-takeoff" || name === "flight-land" || name === "flight-confirm-landing" || name === "flight-return-home" || name === "flight-stop-takeoff" || name === "flight-stop-auto-landing" || name === "flight-stop-go-home") {
      const linkIssue = msdkInvocationIssue(device);
      if (linkIssue !== null) return reject(linkIssue);
      if (name === "flight-confirm-landing" && read(read(device, "connection"), "landingConfirmationNeeded") !== true) {
        return reject("\u98DE\u63A7\u5F53\u524D\u672A\u8BF7\u6C42\u786E\u8BA4\u964D\u843D\uFF0C\u8BF7\u67E5\u770B\u5F53\u524D\u98DE\u884C\u72B6\u6001");
      }
      return accept();
    }
    if (name === "mission-stage") {
      const phase2 = text(read(current.mission, "phase"));
      if (phase2 !== null && phase2 !== "idle" && phase2 !== "completed" && phase2 !== "failed" && phase2 !== "disconnected") return reject("\u5F53\u524D\u4EFB\u52A1\u5C1A\u672A\u7ED3\u675F\uFF0C\u4E0D\u80FD\u91CD\u65B0\u51C6\u5907\u822A\u7EBF");
      return current.selectedRoute?.executable === true ? accept() : reject(current.selectedRoute?.blockedReason ?? "\u5F53\u524D\u822A\u7EBF\u4E0D\u80FD\u63D0\u4EA4\u7ED9\u98DE\u673A");
    }
    if (name === "mission-upload") {
      const phase2 = text(read(current.mission, "phase"));
      if (phase2 !== "staged") return reject("\u8BF7\u5148\u5C06\u822A\u7EBF\u4F20\u8F93\u5230\u624B\u673A");
      const msdkIssue2 = msdkInvocationIssue(device);
      return msdkIssue2 === null ? accept() : reject(msdkIssue2);
    }
    if (name === "mission-pause") {
      if (text(read(current.mission, "phase")) !== "running" && text(read(current.mission, "phase")) !== "starting") return reject("\u5F53\u524D\u9636\u6BB5\u4E0D\u80FD\u6682\u505C");
      const msdkIssue2 = msdkInvocationIssue(device);
      return msdkIssue2 === null ? accept() : reject(msdkIssue2);
    }
    if (name === "mission-resume") {
      if (text(read(current.mission, "phase")) !== "paused") return reject("\u5F53\u524D\u9636\u6BB5\u4E0D\u80FD\u6062\u590D");
      const msdkIssue2 = msdkInvocationIssue(device);
      return msdkIssue2 === null ? accept() : reject(msdkIssue2);
    }
    if (name === "mission-stop") {
      const phase2 = text(read(current.mission, "phase"));
      if (phase2 !== "starting" && phase2 !== "running" && phase2 !== "pausing" && phase2 !== "paused" && phase2 !== "resuming" && phase2 !== "stopping" && phase2 !== "disconnected") return reject("\u5F53\u524D\u9636\u6BB5\u4E0D\u80FD\u505C\u6B62\u822A\u7EBF");
      const msdkIssue2 = msdkInvocationIssue(device);
      return msdkIssue2 === null ? accept() : reject(msdkIssue2);
    }
    if (name !== "mission-start") return reject("\u672A\u77E5\u64CD\u4F5C");
    const phase = text(read(current.mission, "phase"));
    if (phase !== "uploaded" && phase !== "starting" && phase !== "pausing" && phase !== "resuming") return reject("\u8BF7\u5148\u5C06\u5F53\u524D\u822A\u7EBF\u4E0A\u4F20\u5230\u6240\u9009\u98DE\u673A");
    const msdkIssue = msdkInvocationIssue(device);
    return msdkIssue === null ? accept() : reject(msdkIssue);
  }
  var OperatorConsole = freeze4({ project, evaluate: evaluate4 });

  // src/production/operator-console/action-arm/index.ts
  var freeze5 = (value) => Object.freeze(value);
  var ARMABLE_ACTIONS = Object.freeze(["flight-takeoff", "flight-land", "flight-return-home"]);
  var SAME_CLICK_FLIGHT_CONFIRM = Object.freeze([
    "flight-confirm-landing",
    "flight-stop-takeoff",
    "flight-stop-auto-landing",
    "flight-stop-go-home"
  ]);
  var ARM_SETTLE_MS = 300;
  var ARM_HOLD_MS = 5e3;
  var IDLE_LABELS = Object.freeze({
    "flight-takeoff": "\u8D77\u98DE",
    "flight-land": "\u964D\u843D",
    "flight-return-home": "\u8FD4\u822A"
  });
  var ARMED_LABELS = Object.freeze({
    "flight-takeoff": "\u786E\u8BA4\u8D77\u98DE",
    "flight-land": "\u786E\u8BA4\u964D\u843D",
    "flight-return-home": "\u786E\u8BA4\u8FD4\u822A"
  });
  var isArmableAction = (action) => ARMABLE_ACTIONS.includes(action);
  var isSameClickFlightConfirm = (action) => SAME_CLICK_FLIGHT_CONFIRM.includes(action);
  function interpretArmClick(armed, action, nowMs) {
    if (!isArmableAction(action)) return freeze5({ kind: "disarm", next: null });
    if (armed === null || armed.action !== action || nowMs - armed.armedAtMs >= ARM_HOLD_MS) {
      return freeze5({ kind: "arm", next: freeze5({ action, armedAtMs: nowMs }) });
    }
    if (nowMs - armed.armedAtMs < ARM_SETTLE_MS) return freeze5({ kind: "ignore", next: armed });
    return freeze5({ kind: "confirm", next: null });
  }
  function expireArm(armed, nowMs) {
    if (armed === null) return null;
    return nowMs - armed.armedAtMs >= ARM_HOLD_MS ? null : armed;
  }
  function buttonLabel(action, armed) {
    return armed !== null && armed.action === action ? ARMED_LABELS[action] : IDLE_LABELS[action];
  }
  var flightWorkflowAction = (uiAction) => uiAction.startsWith("flight-") ? uiAction.slice("flight-".length) : uiAction;
  var liveConfirmation = (pending, nowMs) => pending !== null && pending.expiresAtMs > nowMs ? pending : null;
  function confirmationMatchesClick(pending, requested) {
    const live = liveConfirmation(pending, requested.nowMs);
    return live !== null && requested.deviceId !== null && live.deviceId === requested.deviceId && live.action === flightWorkflowAction(requested.uiAction);
  }
  function confirmationCreatedByRequest(pending, previousConfirmationId, requested) {
    return confirmationMatchesClick(pending, requested) && pending.confirmationId !== previousConfirmationId;
  }
  function flightConfirmDispatch(pending, requested) {
    return confirmationMatchesClick(pending, requested) ? freeze5({ kind: "dispatch", confirmation: pending }) : freeze5({ kind: "wait" });
  }
  function sameClickConfirmDispatch(pending, previousConfirmationId, requested) {
    return confirmationCreatedByRequest(pending, previousConfirmationId, requested) ? freeze5({ kind: "dispatch", confirmation: pending }) : freeze5({ kind: "wait" });
  }
  function adoptPendingConfirmation(local, snapshot2, nowMs) {
    return liveConfirmation(local, nowMs) ?? liveConfirmation(snapshot2, nowMs);
  }
  var ActionArm = Object.freeze({
    ARMABLE_ACTIONS,
    SAME_CLICK_FLIGHT_CONFIRM,
    ARM_SETTLE_MS,
    ARM_HOLD_MS,
    IDLE_LABELS,
    ARMED_LABELS,
    isArmableAction,
    isSameClickFlightConfirm,
    interpretArmClick,
    expireArm,
    buttonLabel,
    flightWorkflowAction,
    confirmationMatchesClick,
    confirmationCreatedByRequest,
    flightConfirmDispatch,
    sameClickConfirmDispatch,
    adoptPendingConfirmation
  });

  // src/production/operator-console/flight-now-status/index.ts
  var freeze6 = (value) => Object.freeze(value);
  var EMPTY_STATUS = "\u2014";
  var text2 = (value) => typeof value === "string" && value.trim().length > 0 ? value : null;
  var finite2 = (value) => typeof value === "number" && Number.isFinite(value) ? value : null;
  function commandReachStatus(input) {
    if (!input.selected) return "\u672A\u9009\u62E9\u624B\u673A";
    if (!input.relayOnline) return "\u4E2D\u7EE7\u672A\u8FDE\u63A5";
    if (!input.msdkReady) return "MSDK \u672A\u5C31\u7EEA";
    if (input.airLinkConnected === false) return "AirLink \u672A\u8FDE\u63A5";
    if (input.cameraConnected === false) return "\u4E3B\u76F8\u673A\u672A\u8FDE\u63A5";
    return input.airLinkConnected === true && input.cameraConnected === true ? "\u53EF\u63A8\u6D41" : "\u53EF\u4E0B\u53D1";
  }
  function streamPushStatus(input) {
    if (!input.selected) return "\u672A\u9009\u62E9\u624B\u673A";
    if (input.streaming === true) {
      const details = [input.resolution, input.fps === null ? null : `${input.fps} fps`].filter((part) => part !== null && part.length > 0);
      return details.length > 0 ? `\u63A8\u6D41\u4E2D \xB7 ${details.join(" \xB7 ")}` : "\u63A8\u6D41\u4E2D";
    }
    if (input.streaming === false) return "\u672A\u63A8\u6D41";
    return EMPTY_STATUS;
  }
  function streamPaintStatus(input) {
    if (!input.selected) return "\u672A\u9009\u62E9\u624B\u673A";
    if (input.painting === true) return "\u6B63\u5728\u51FA\u753B";
    if (input.painting === false) return "\u672A\u51FA\u753B";
    return EMPTY_STATUS;
  }
  function streamErrorStatus(input) {
    if (!input.selected) return "\u672A\u9009\u62E9\u624B\u673A";
    if (input.code === null && input.description === null) return EMPTY_STATUS;
    return [input.code, input.description].filter((part) => part !== null).join(" ");
  }
  function missionExecutionNowStatus(input) {
    if (!input.selected) return "\u672A\u9009\u62E9\u624B\u673A";
    const parts = [input.execution, input.waypoint, input.fileName].filter((part) => part !== null && part.length > 0);
    return parts.length > 0 ? parts.join(" \xB7 ") : EMPTY_STATUS;
  }
  function missionUploadOrActionStatus(input) {
    if (!input.selected) return "\u672A\u9009\u62E9\u624B\u673A";
    if (typeof input.uploadPercent === "number" && Number.isSafeInteger(input.uploadPercent) && input.uploadPercent >= 0 && input.uploadPercent < 100) {
      return `\u4E0A\u4F20\u4E2D ${input.uploadPercent}%`;
    }
    if (input.action !== null && input.action.length > 0) return input.action;
    return EMPTY_STATUS;
  }
  function interruptStatus(input) {
    return streamErrorStatus(input);
  }
  function directProcessStatus(input) {
    if (!input.selected) return "\u672A\u9009\u62E9\u624B\u673A";
    if (input.flying === "grounded" && input.motorsOn === false) return "\u5730\u9762";
    if (input.motorsOn === true && input.flying !== "flying") return "\u8D77\u98DE\u4E2D";
    if (input.flying === "flying") {
      const mode = input.flightMode ?? "";
      if (mode === "AUTO_LANDING" || mode === "CONFIRM_LANDING" || input.landingConfirmationNeeded === true) return "\u964D\u843D\u4E2D";
      if (mode === "GO_HOME" || mode === "AUTO_RETURN" || input.lowBatteryRthState === "COUNTING_DOWN" || input.lowBatteryRthState === "EXECUTED") {
        return "\u8FD4\u822A\u4E2D";
      }
      return "\u98DE\u884C\u4E2D";
    }
    return EMPTY_STATUS;
  }
  function directAlertStatus(input) {
    if (!input.selected) return "\u672A\u9009\u62E9\u624B\u673A";
    if (input.takeoffFailure !== null && input.takeoffFailure.length > 0) return input.takeoffFailure;
    if (input.motorStartFailure !== null && input.motorStartFailure.length > 0) return input.motorStartFailure;
    if (input.visionWarning !== null && input.visionWarning.length > 0) return input.visionWarning;
    return EMPTY_STATUS;
  }
  function hudFlying(state2) {
    if (state2 === "flying") return "\u98DE\u884C\u4E2D";
    if (state2 === "grounded") return "\u672A\u98DE\u884C";
    return EMPTY_STATUS;
  }
  function hudMotors(motorsOn) {
    if (motorsOn === true) return "\u7535\u673A\u5F00";
    if (motorsOn === false) return "\u7535\u673A\u5173";
    return EMPTY_STATUS;
  }
  function hudBattery(percent) {
    const value = finite2(percent);
    return value !== null && value >= 0 && value <= 100 ? `${Math.round(value)}%` : EMPTY_STATUS;
  }
  function hudAltitude(meters) {
    const value = finite2(meters);
    return value === null ? EMPTY_STATUS : `${value.toFixed(1)} m`;
  }
  function hudText(value) {
    return text2(value) ?? EMPTY_STATUS;
  }
  var TAKEOFF_MODES = /* @__PURE__ */ new Set(["AUTO_TAKEOFF"]);
  var LAND_MODES = /* @__PURE__ */ new Set(["AUTO_LANDING", "FORCE_LANDING", "CONFIRM_LANDING"]);
  var HOME_MODES = /* @__PURE__ */ new Set(["GO_HOME", "AUTO_RETURN"]);
  var PHONE_MISSION = /* @__PURE__ */ new Set(["STARTING", "EXECUTING", "PAUSED"]);
  var DJI_MISSION = /* @__PURE__ */ new Set(["PREPARING", "RECOVERING", "ENTER_WAYLINE", "EXECUTING", "PAUSED", "RETURN_TO_START_POINT"]);
  var DJI_MISSION_IDLE = /* @__PURE__ */ new Set(["IDLE", "READY", "UPLOADING", "FINISHED", "INTERRUPTED", "DISCONNECTED", "NOT_SUPPORTED", "UNKNOWN"]);
  function monitorAircraftStatus(input) {
    if (input.flightController === "disconnected") return { label: "\u98DE\u63A7\u672A\u8FDE\u63A5", detail: "" };
    const mode = input.flightMode;
    const flying = input.flying === "flying" || input.flying === "grounded" ? input.flying : null;
    const djiActive = input.djiMissionState !== null && DJI_MISSION.has(input.djiMissionState);
    const djiIdle = input.djiMissionState !== null && DJI_MISSION_IDLE.has(input.djiMissionState);
    const phoneActive = input.missionExecution !== null && PHONE_MISSION.has(input.missionExecution);
    const missionActive = djiActive || phoneActive && !djiIdle;
    const aircraftExecuting = input.djiMissionState === "EXECUTING" || input.djiMissionState === "ENTER_WAYLINE" || input.djiMissionState === "RETURN_TO_START_POINT";
    const paused = input.djiMissionState === "PAUSED" || input.missionExecution === "PAUSED" && !aircraftExecuting;
    const starting = !paused && (input.djiMissionState === "PREPARING" || input.djiMissionState === "RECOVERING" || input.djiMissionState === "ENTER_WAYLINE" || input.missionExecution === "STARTING" && !aircraftExecuting && input.djiMissionState !== "PAUSED");
    const protection = input.landingProtectionState === "NOT_SAFE_TO_LAND" ? "\u5F53\u524D\u4E0D\u9002\u5408\u964D\u843D" : "";
    const waypoint = typeof input.waypointIndex === "number" && Number.isSafeInteger(input.waypointIndex) && input.waypointIndex >= 0 ? `\u822A\u70B9 ${input.waypointIndex}` : "";
    const returningHome = mode !== null && HOME_MODES.has(mode) || flying === "flying" && input.lowBatteryRthState === "COUNTING_DOWN";
    if (flying === "grounded" && input.motorsOn === false) {
      return { label: "\u5730\u9762", detail: "" };
    }
    if (input.landingConfirmationNeeded === true) {
      return { label: "\u7EE7\u7EED\u964D\u843D", detail: protection };
    }
    if (mode !== null && LAND_MODES.has(mode)) {
      return { label: "\u964D\u843D", detail: protection };
    }
    if (returningHome) {
      return { label: "\u8FD4\u822A", detail: "" };
    }
    if (flying === "flying" && missionActive) {
      const detail = [
        paused ? "\u5DF2\u6682\u505C" : starting ? "\u542F\u52A8\u4E2D" : "",
        input.djiMissionState === "RETURN_TO_START_POINT" ? "\u8FD4\u56DE\u8D77\u70B9" : "",
        waypoint
      ].filter((part) => part.length > 0).join(" \xB7 ");
      return { label: "\u6267\u884C\u822A\u7EBF", detail };
    }
    if (flying === "grounded" && input.motorsOn === true || mode !== null && TAKEOFF_MODES.has(mode)) {
      return { label: "\u8D77\u98DE", detail: "" };
    }
    if (flying === "flying") {
      return { label: "\u60AC\u505C", detail: mode ?? "" };
    }
    return { label: "\u672A\u786E\u8BA4", detail: "" };
  }
  var WAYLINE_STOP_DJI = /* @__PURE__ */ new Set(["PREPARING", "RECOVERING", "ENTER_WAYLINE", "EXECUTING", "PAUSED", "RETURN_TO_START_POINT"]);
  var WAYLINE_STOP_PHONE = /* @__PURE__ */ new Set(["STARTING", "EXECUTING", "PAUSED", "STOPPING"]);
  function monitorHoverCommand(input) {
    if (input.flightController === "disconnected") return freeze6({ kind: "none", state: "failed", detail: "\u98DE\u63A7\u672A\u8FDE\u63A5" });
    const mode = input.flightMode;
    const flying = input.flying === "flying" || input.flying === "grounded" ? input.flying : null;
    const wayline = input.djiMissionState !== null && WAYLINE_STOP_DJI.has(input.djiMissionState) || input.missionExecution !== null && WAYLINE_STOP_PHONE.has(input.missionExecution);
    const returningHome = mode !== null && HOME_MODES.has(mode) || flying === "flying" && input.lowBatteryRthState === "COUNTING_DOWN";
    if (flying === "grounded" && input.motorsOn === false) return freeze6({ kind: "none", state: "done", detail: "\u98DE\u673A\u5728\u5730\u9762" });
    if (input.landingConfirmationNeeded === true || mode === "CONFIRM_LANDING") return freeze6({ kind: "flight", action: "stop-auto-landing", detail: "\u6B63\u5728\u505C\u6B62\u81EA\u52A8\u964D\u843D\uFF0C\u98DE\u673A\u60AC\u505C" });
    if (mode !== null && LAND_MODES.has(mode)) return freeze6({ kind: "flight", action: "stop-auto-landing", detail: "\u6B63\u5728\u505C\u6B62\u81EA\u52A8\u964D\u843D\uFF0C\u98DE\u673A\u60AC\u505C" });
    if (returningHome) return freeze6({ kind: "flight", action: "stop-go-home", detail: "\u6B63\u5728\u9000\u51FA\u8FD4\u822A\uFF0C\u98DE\u673A\u60AC\u505C" });
    if (wayline) return freeze6({ kind: "stop", detail: "\u6B63\u5728\u505C\u6B62\u822A\u7EBF\uFF0C\u98DE\u673A\u60AC\u505C" });
    if (flying === "grounded" && input.motorsOn === true || mode !== null && TAKEOFF_MODES.has(mode)) return freeze6({ kind: "flight", action: "stop-takeoff", detail: "\u6B63\u5728\u505C\u6B62\u81EA\u52A8\u8D77\u98DE\uFF0C\u98DE\u673A\u60AC\u505C" });
    if (flying === "flying") return freeze6({ kind: "none", state: "done", detail: "\u98DE\u673A\u5DF2\u5728\u60AC\u505C" });
    return freeze6({ kind: "none", state: "failed", detail: "\u98DE\u63A7\u72B6\u6001\u672A\u786E\u8BA4\uFF0C\u672A\u53D1\u9001\u60AC\u505C" });
  }
  var ROUTE_EXECUTING_DJI = /* @__PURE__ */ new Set(["EXECUTING", "ENTER_WAYLINE", "RETURN_TO_START_POINT"]);
  function monitorRouteControl(input) {
    const phase = input.missionPhase;
    const dji = input.djiMissionState;
    const phone = input.missionExecution;
    const djiExecuting = dji !== null && ROUTE_EXECUTING_DJI.has(dji);
    const aircraftPaused = dji === "PAUSED" || phone === "PAUSED" && !djiExecuting;
    const aircraftExecuting = djiExecuting || phone === "EXECUTING";
    if (phase === "stopping" || phone === "STOPPING") return "stopping";
    if (phase === "resuming") return aircraftExecuting ? "pause" : "resume";
    if (aircraftPaused && phase !== "starting") return "resume";
    if (phase === "paused") return "resume";
    if (phase === "pausing") return "pause";
    if (aircraftExecuting || phase === "running" || input.routeExecutionStarted && phase === "starting") return "pause";
    if (phase === "starting" || phone === "STARTING") return "await";
    return "choose";
  }
  function monitorDirectFlight(input) {
    const mode = input.flightMode;
    const flying = input.flying === "flying" || input.flying === "grounded" ? input.flying : null;
    const onGround = flying === "grounded" && input.motorsOn === false;
    const confirmLanding = !onGround && input.landingConfirmationNeeded === true;
    const returningHome = !onGround && !confirmLanding && (mode !== null && HOME_MODES.has(mode) || flying === "flying" && input.lowBatteryRthState === "COUNTING_DOWN");
    return freeze6({
      home: returningHome ? freeze6({ label: "\u505C\u6B62\u8FD4\u822A", mode: "stop-go-home" }) : freeze6({ label: "\u8FD4\u822A", mode: "return-home" }),
      land: confirmLanding ? freeze6({ label: "\u786E\u8BA4\u964D\u843D", mode: "confirm-landing" }) : freeze6({ label: "\u964D\u843D", mode: "land" })
    });
  }
  function monitorStreamToggle(input) {
    if (!input.present) return { label: "\u542F\u52A8\u56FE\u4F20", enabled: false, mode: "idle", title: "\u7A7A\u4F4D" };
    const sourceGone = input.streamPhase === "failed" && input.failureCode === "SOURCE_UNAVAILABLE";
    const heldByPhone = input.phoneStreaming && !input.releasePhoneHold && !sourceGone;
    const session = !sourceGone && (input.streamPhase === "starting" || input.streamPhase === "streaming" || input.streamPhase === "stopping" || input.videoPhase === "ready" || input.videoPhase === "awaiting-playback" || input.videoPhase === "awaiting-ingest" || input.playing);
    if (heldByPhone || session) return { label: "\u505C\u6B62\u56FE\u4F20", enabled: true, mode: "stop", title: "\u505C\u6B62\u56FE\u4F20" };
    if (!input.sdkReady) return { label: "\u542F\u52A8\u56FE\u4F20", enabled: false, mode: "idle", title: "\u624B\u673A\u5C1A\u672A\u5C31\u7EEA\uFF0C\u65E0\u6CD5\u542F\u52A8\u56FE\u4F20" };
    if (input.airLink !== "connected") {
      return {
        label: "\u542F\u52A8\u56FE\u4F20",
        enabled: false,
        mode: "idle",
        title: input.airLink === "disconnected" ? "AirLink \u672A\u8FDE\u63A5\uFF0C\u65E0\u6CD5\u542F\u52A8\u56FE\u4F20" : "AirLink \u72B6\u6001\u672A\u77E5\uFF0C\u65E0\u6CD5\u542F\u52A8\u56FE\u4F20"
      };
    }
    if (input.camera !== "connected") {
      return {
        label: "\u542F\u52A8\u56FE\u4F20",
        enabled: false,
        mode: "idle",
        title: input.camera === "disconnected" ? "\u4E3B\u76F8\u673A\u672A\u8FDE\u63A5\uFF0C\u65E0\u6CD5\u542F\u52A8\u56FE\u4F20" : "\u4E3B\u76F8\u673A\u72B6\u6001\u672A\u77E5\uFF0C\u65E0\u6CD5\u542F\u52A8\u56FE\u4F20"
      };
    }
    return { label: "\u542F\u52A8\u56FE\u4F20", enabled: true, mode: "start", title: "\u542F\u52A8\u8FD9\u67B6\u98DE\u673A\u7684\u56FE\u4F20" };
  }
  var FlightNowStatus = freeze6({
    EMPTY_STATUS,
    commandReachStatus,
    streamPushStatus,
    streamPaintStatus,
    streamErrorStatus,
    missionExecutionNowStatus,
    missionUploadOrActionStatus,
    interruptStatus,
    directProcessStatus,
    directAlertStatus,
    hudFlying,
    hudMotors,
    hudBattery,
    hudAltitude,
    hudText,
    monitorAircraftStatus,
    monitorHoverCommand,
    monitorRouteControl,
    monitorDirectFlight,
    monitorStreamToggle
  });

  // src/production/operator-console/renderer/operation-feedback.ts
  var record2 = (value) => value !== null && typeof value === "object" && !Array.isArray(value) ? value : null;
  var read2 = (value, key) => {
    const source = record2(value);
    if (source === null) return void 0;
    try {
      return source[key];
    } catch {
      return void 0;
    }
  };
  var text3 = (value, maxLength = 512) => typeof value === "string" && value.trim().length > 0 && Array.from(value).length <= maxLength && !/[\p{Cc}]/u.test(value) ? value : null;
  var unwrap = (value) => {
    let current = value;
    for (let step = 0; step < 4; step += 1) {
      const source = record2(current);
      if (source === null || source.ok !== true || !Object.hasOwn(source, "value")) return current;
      current = source.value;
    }
    return current;
  };
  var flightLabel = (action) => {
    if (action === "takeoff") return "\u8D77\u98DE";
    if (action === "land") return "\u964D\u843D";
    if (action === "confirm-landing") return "\u786E\u8BA4\u7EE7\u7EED\u964D\u843D";
    if (action === "return-home" || action === "returnHome" || action === "rth") return "\u8FD4\u822A";
    if (action === "stop-takeoff") return "\u505C\u6B62\u81EA\u52A8\u8D77\u98DE";
    if (action === "stop-auto-landing") return "\u505C\u6B62\u81EA\u52A8\u964D\u843D";
    if (action === "stop-go-home") return "\u9000\u51FA\u8FD4\u822A";
    return text3(action, 64) ?? "\u8BE5\u52A8\u4F5C";
  };
  var missionLabel = (action) => {
    if (action === "mission-upload") return "\u4E0A\u4F20\u81F3\u98DE\u673A";
    if (action === "mission-start") return "\u6267\u884C\u822A\u7EBF";
    if (action === "mission-pause") return "\u6682\u505C\u822A\u7EBF";
    if (action === "mission-resume") return "\u6062\u590D\u822A\u7EBF";
    if (action === "mission-stop") return "\u505C\u6B62\u822A\u7EBF";
    return "\u822A\u7EBF\u64CD\u4F5C";
  };
  var streamLabel = (action) => action === "stream-stop" ? "\u505C\u6B62\u56FE\u4F20" : "\u542F\u52A8\u56FE\u4F20";
  var photoLabel = (action) => action === "photo-fetch" ? "\u56DE\u4F20\u7167\u7247" : "\u62CD\u7167";
  var pairingLabel = (action) => action === "pairing-stop" ? "\u505C\u6B62\u5BF9\u9891" : "\u5F00\u59CB\u5BF9\u9891";
  var localReason = (code) => {
    if (code === "DEVICE_OFFLINE" || code === "RELAY_OFFLINE") return "\u624B\u673A\u4E2D\u7EE7\u79BB\u7EBF";
    if (code === "SDK_NOT_READY") return "\u624B\u673A\u7AEF MSDK \u5C1A\u672A\u5C31\u7EEA";
    if (code === "LANDING_CONFIRMATION_NOT_REQUIRED") return "\u98DE\u63A7\u5F53\u524D\u672A\u8BF7\u6C42\u786E\u8BA4\u964D\u843D\uFF0C\u8BF7\u67E5\u770B\u5F53\u524D\u98DE\u884C\u72B6\u6001";
    if (code === "NOTHING_TO_FETCH") return "\u98DE\u673A\u4E0A\u6CA1\u6709\u7535\u8111\u7F3A\u5C11\u7684\u7167\u7247";
    if (code === "AIRLINK_OFFLINE") return "AirLink \u672A\u8FDE\u63A5";
    if (code === "AIRLINK_CONNECTION_UNKNOWN") return "AirLink \u72B6\u6001\u672A\u77E5";
    if (code === "CAMERA_OFFLINE") return "\u4E3B\u76F8\u673A\u672A\u8FDE\u63A5";
    if (code === "CAMERA_CONNECTION_UNKNOWN") return "\u4E3B\u76F8\u673A\u72B6\u6001\u672A\u77E5";
    if (code === "CAPABILITY_BLOCKED") return "\u5FC5\u8981\u7684\u53EF\u8FBE\u6027\u6761\u4EF6\u672A\u6EE1\u8DB3";
    if (code === "OPERATION_IN_PROGRESS") return "\u4E0A\u4E00\u6761\u547D\u4EE4\u4ECD\u5728\u5904\u7406";
    if (code === "ILLEGAL_PHASE") return "\u5F53\u524D\u9636\u6BB5\u4E0D\u80FD\u4ECE\u5934\u91CD\u505A\u8FD9\u4E00\u6B65";
    if (code === "TASK_ACTIVE") return "\u5F53\u524D\u4EFB\u52A1\u8FD8\u5728\u8FDB\u884C\uFF0C\u4E0D\u80FD\u91CD\u65B0\u51C6\u5907\u822A\u7EBF";
    if (code === "INVALID_INPUT") return "\u8F93\u5165\u65E0\u6548";
    if (code === "DISPOSED") return "\u64CD\u4F5C\u6A21\u5757\u5DF2\u505C\u6B62";
    if (code === "DEPENDENCY_FAILURE") return "\u684C\u9762\u4F9D\u8D56\u4E0D\u53EF\u7528";
    if (code === "CONFIRMATION_EXPIRED") return "\u4EBA\u5DE5\u786E\u8BA4\u5DF2\u8FC7\u671F";
    if (code === "CONFIRMATION_MISMATCH") return "\u4EBA\u5DE5\u786E\u8BA4\u4E0E\u5F53\u524D\u64CD\u4F5C\u4E0D\u5339\u914D";
    if (code === "NO_PENDING_CONFIRMATION") return "\u6CA1\u6709\u5F85\u786E\u8BA4\u7684\u64CD\u4F5C";
    return code === null ? "\u672A\u6EE1\u8DB3\u672C\u5730\u5FC5\u8981\u6761\u4EF6" : code;
  };
  var confirmationAction = (inner) => {
    const direct = text3(read2(inner, "action"), 64);
    if (direct !== null) return direct;
    return text3(read2(read2(inner, "confirmation"), "action"), 64);
  };
  var msdkCallback = (source, outcome, status, rawType, details = []) => ({
    source,
    outcome,
    message: [`MSDK \u56DE\u8C03\uFF1A${status}`, `\u539F\u59CB\u7C7B\u578B\uFF1A${rawType}`, ...details].join("\n")
  });
  var djiFailure = (inner) => {
    const nested = record2(read2(inner, "value"));
    const platformError = read2(inner, "platformError") ?? read2(nested, "platformError");
    const code = text3(read2(platformError, "code")) ?? text3(read2(inner, "errorCode")) ?? text3(read2(nested, "errorCode"));
    const description = text3(read2(platformError, "description")) ?? text3(read2(inner, "errorDescription")) ?? text3(read2(nested, "errorDescription"));
    const details = [
      ...code === null ? [] : [`\u9519\u8BEF\u7801\uFF1A${code}`],
      ...description === null ? [] : [`\u9519\u8BEF\u8BF4\u660E\uFF1A${description}`]
    ];
    return msdkCallback("dji", "rejected", "\u5931\u8D25", "onFailure", details);
  };
  var unconfirmed = (label, explanation = `\u672A\u6536\u5230 DJI MSDK ${label} \u7684\u6700\u7EC8\u56DE\u8C03\uFF1B\u4E0D\u80FD\u636E\u6B64\u5224\u65AD\u547D\u4EE4\u662F\u5426\u6267\u884C`) => msdkCallback("relay", "unconfirmed", "\u672A\u786E\u8BA4", "\u65E0\u6700\u7EC8\u56DE\u8C03", [`\u8BF4\u660E\uFF1A${explanation}`]);
  var operationLabel = (action, flightAction) => action.startsWith("flight-") ? flightAction : action.startsWith("mission-") ? missionLabel(action) : action.startsWith("photo-") ? photoLabel(action) : action.startsWith("pairing-") ? pairingLabel(action) : streamLabel(action);
  var operationFeedback = (action, value) => {
    const inner = unwrap(value);
    const code = text3(read2(inner, "code")) ?? text3(read2(value, "code"));
    const confirmedAction = confirmationAction(inner);
    const platformAction = flightLabel(confirmedAction);
    if (code === "FLIGHT_ACTION_REJECTED") return djiFailure(inner);
    if (code === "WAYLINE_ACTION_REJECTED") return djiFailure(inner);
    if (code === "STREAM_ACTION_REJECTED") return djiFailure(inner);
    if (code === "PHOTO_ACTION_REJECTED") {
      const nested = record2(read2(inner, "value"));
      const platformError = read2(inner, "platformError") ?? read2(nested, "platformError");
      const errorCode = text3(read2(platformError, "code")) ?? text3(read2(inner, "errorCode")) ?? text3(read2(nested, "errorCode"));
      const description = text3(read2(platformError, "description")) ?? text3(read2(inner, "errorDescription")) ?? text3(read2(nested, "errorDescription"));
      if (description?.includes("source=relay-preflight") && (errorCode === "STORAGE_NOT_READY" || errorCode === "STORAGE_STATUS_UNAVAILABLE")) {
        const explanation = errorCode === "STORAGE_STATUS_UNAVAILABLE" ? "\u624B\u673A\u6682\u65F6\u8BFB\u4E0D\u5230\u98DE\u673A\u7684\u5B58\u50A8\u72B6\u6001\uFF0C\u5C1A\u672A\u6309\u5FEB\u95E8\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5\u3002" : "\u624B\u673A\u62CD\u7167\u524D\u68C0\u67E5\u672A\u901A\u8FC7\uFF0C\u5C1A\u672A\u6309\u5FEB\u95E8\uFF1A\u98DE\u673A\u672A\u786E\u8BA4\u5F53\u524D\u5B58\u50A8\u80FD\u4FDD\u5B58\u7167\u7247\u3002\u8BF7\u5728 DJI Fly \u68C0\u67E5\u5B58\u50A8\u4F4D\u7F6E\u548C\u5F53\u524D\u753B\u8D28\u4E0B\u7684\u53EF\u62CD\u5F20\u6570\u3002";
        return { source: "relay", outcome: "not-called", message: `${explanation}
\u68C0\u67E5\u7ED3\u679C\uFF1A${errorCode}
\u8BE6\u7EC6\u72B6\u6001\uFF1A${description}` };
      }
      if (errorCode === "CAMERA_MODE_NOT_PHOTO") {
        return { source: "relay", outcome: "rejected", message: "\u76F8\u673A\u6CA1\u6709\u8FDB\u5165\u62CD\u7167\u6A21\u5F0F\uFF0C\u753B\u9762\u4F1A\u6062\u590D\u3002\u56FE\u4F20\u6CA1\u6709\u505C\u6B62\u3002" };
      }
      if (errorCode === "STORAGE_NOT_READY") {
        const failure3 = djiFailure(inner);
        return { ...failure3, message: `\u98DE\u673A\u7684\u7167\u7247\u5B58\u50A8\u5C1A\u672A\u5C31\u7EEA\uFF0C\u8BF7\u68C0\u67E5\u5B58\u50A8\u4F4D\u7F6E\u3001\u53EF\u7528\u7A7A\u95F4\u53CA\u5B58\u50A8\u5361\u72B6\u6001\u3002
${failure3.message}` };
      }
      return djiFailure(inner);
    }
    if (code === "RESULT_UNCONFIRMED" || code !== null && code.endsWith("_UNCONFIRMED")) return unconfirmed(operationLabel(action, platformAction));
    if (code === "FLIGHT_ACTION_INVOCATION_FAILED" || code === "WAYLINE_ACTION_INVOCATION_FAILED" || code === "STREAM_ACTION_INVOCATION_FAILED" || code === "INVOCATION_FAILED" || code === "TRANSFER_FAILED") {
      return unconfirmed(operationLabel(action, platformAction), `\u624B\u673A\u672A\u53D6\u5F97 DJI MSDK ${operationLabel(action, platformAction)} \u7684\u53EF\u7528\u7ED3\u679C`);
    }
    if (code === "DEPENDENCY_FAILURE" || code === "WAYLINE_UPLOAD_FAILED" || code === "WAYLINE_START_FAILED" || code === "WAYLINE_PAUSE_FAILED" || code === "WAYLINE_RESUME_FAILED" || code === "WAYLINE_STOP_FAILED") return unconfirmed(operationLabel(action, platformAction), `\u672A\u6536\u5230 DJI MSDK ${operationLabel(action, platformAction)}\u7684\u53EF\u5224\u5B9A\u7ED3\u679C`);
    if (code === "RELAY_REJECTED") {
      const reason2 = text3(read2(inner, "reason")) ?? text3(read2(value, "reason"));
      return { source: "relay", outcome: "rejected", message: `\u624B\u673A/\u4E2D\u7EE7\u56DE\u8C03\uFF1A\u62D2\u7EDD${operationLabel(action, platformAction)}${reason2 === null ? "" : `\uFF1B\u539F\u56E0\uFF1A${reason2}`}` };
    }
    if (code === "CONFIRMATION_REQUIRED" && read2(inner, "confirmation") !== void 0) {
      const label = action.startsWith("mission-") ? missionLabel(action) : flightLabel(confirmedAction);
      return { source: "desktop", outcome: "pending", message: `\u7B49\u5F85\u4EBA\u5DE5\u786E\u8BA4\uFF1A${label} \u5C1A\u672A\u8C03\u7528 DJI MSDK` };
    }
    if (code === "CANCELLED" && read2(inner, "confirmation") !== void 0) {
      return { source: "desktop", outcome: "completed", message: `\u5DF2\u53D6\u6D88${flightLabel(confirmedAction)}\uFF0C\u672A\u8C03\u7528 DJI MSDK` };
    }
    if (code === "CAPTURED") return msdkCallback("dji", "accepted", "\u6210\u529F", "onSuccess", ["\u8BF4\u660E\uFF1A\u5DF2\u62CD\u4E0B\u539F\u56FE\uFF0C\u7535\u8111\u5C1A\u672A\u6536\u9F50\u6587\u4EF6"]);
    if (code === "NOTHING_TO_FETCH") return { source: "relay", outcome: "completed", message: "\u98DE\u673A\u4E0A\u6CA1\u6709\u7535\u8111\u7F3A\u5C11\u7684\u7167\u7247" };
    const reason = text3(read2(inner, "reason")) ?? text3(read2(value, "reason"));
    if (code === "PREFLIGHT_BLOCKED") {
      const blockers = read2(inner, "blockers");
      const reasons = Array.isArray(blockers) ? blockers.map((item) => localReason(text3(read2(item, "code")))) : [];
      return { source: "desktop", outcome: "not-called", message: `\u672A\u8C03\u7528 DJI MSDK\uFF1A${reasons.length > 0 ? reasons.join("\uFF1B") : localReason(code)}` };
    }
    if (code !== null && code !== "SUCCEEDED") return { source: "desktop", outcome: "not-called", message: `\u672A\u8C03\u7528 DJI MSDK\uFF1A${localReason(reason ?? code)}` };
    if (record2(inner)?.confirmation !== void 0) return { source: "desktop", outcome: "pending", message: `\u7B49\u5F85\u4EBA\u5DE5\u786E\u8BA4\uFF1A${flightLabel(confirmedAction)} \u5C1A\u672A\u8C03\u7528 DJI MSDK` };
    if (action === "mission-stage") return { source: "relay", outcome: "completed", message: "\u624B\u673A\u4E2D\u7EE7\u56DE\u8C03\uFF1A\u822A\u7EBF\u6587\u4EF6\u5DF2\u4F20\u8F93\u5E76\u6821\u9A8C\uFF08\u6B64\u6B65\u9AA4\u672A\u8C03\u7528 DJI MSDK\uFF09" };
    if (action.startsWith("pairing-") && read2(inner, "ok") === true) return { source: "relay", outcome: "completed", message: `\u624B\u673A\u5DF2\u5B8C\u6210${pairingLabel(action)}\u547D\u4EE4\uFF1B\u8BF7\u4EE5\u5BF9\u9891\u72B6\u6001\u4E3A\u51C6` };
    if (code === "SUCCEEDED" && action.startsWith("flight-")) {
      const landing = confirmedAction === "land" || confirmedAction === "confirm-landing" || action === "flight-land" || action === "flight-confirm-landing";
      return msdkCallback("dji", "accepted", "\u6210\u529F", "onSuccess", landing ? ["\u8BF4\u660E\uFF1A\u964D\u843D\u63A5\u53E3\u8C03\u7528\u5DF2\u53D7\u7406\uFF1B\u662F\u5426\u6B63\u5728\u4E0B\u964D\u3001\u662F\u5426\u843D\u5730\u8BF7\u4EE5\u98DE\u63A7\u72B6\u6001\u4E3A\u51C6"] : []);
    }
    if (code === "SUCCEEDED" && action.startsWith("mission-")) return msdkCallback("dji", "accepted", "\u6210\u529F", "onSuccess");
    if (code === "SUCCEEDED" && action.startsWith("stream-")) return msdkCallback("dji", "accepted", "\u6210\u529F", "onSuccess");
    if (code === "SUCCEEDED" && action === "photo-fetch") {
      const count = read2(inner, "count");
      const saved = typeof count === "number" && Number.isInteger(count) && count > 0 ? `\u5DF2\u56DE\u4F20 ${count} \u5F20\u539F\u56FE` : "\u539F\u56FE\u5DF2\u4FDD\u5B58";
      return msdkCallback("dji", "accepted", "\u6210\u529F", "onSuccess", [`\u8BF4\u660E\uFF1A${saved}\uFF0C\u53EF\u7528\u7CFB\u7EDF\u770B\u56FE\u8F6F\u4EF6\u6253\u5F00`]);
    }
    if (code === "SUCCEEDED" && action.startsWith("photo-")) return msdkCallback("dji", "accepted", "\u6210\u529F", "onSuccess");
    if (read2(inner, "ok") === true || read2(value, "ok") === true) return { source: "relay", outcome: "completed", message: "\u624B\u673A\u4E2D\u7EE7\u56DE\u8C03\uFF1A\u5DF2\u5B8C\u6210" };
    return { source: "desktop", outcome: "not-called", message: "\u672A\u8C03\u7528 DJI MSDK\uFF1A\u6CA1\u6709\u83B7\u5F97\u53EF\u8BC6\u522B\u7684\u64CD\u4F5C\u7ED3\u679C" };
  };

  // src/production/operator-console/renderer/render-scheduler.ts
  var RenderDeadlineExceededError = class extends Error {
    constructor() {
      super("Render deadline exceeded");
      this.name = "RenderDeadlineExceededError";
    }
  };
  var browserDeadlines = Object.freeze({
    setTimeout: (callback, milliseconds2) => globalThis.setTimeout(callback, milliseconds2),
    clearTimeout: (handle) => globalThis.clearTimeout(handle)
  });
  function createRenderScheduler(renderOnce, options = { deadlineMs: 5e3 }) {
    if (!Number.isFinite(options.deadlineMs) || options.deadlineMs < 1) throw new Error("Render deadline must be positive");
    const deadlines = options.deadlines ?? browserDeadlines;
    let pending = false;
    let active = null;
    const runNext = () => {
      pending = false;
      const controller = new AbortController();
      return new Promise((resolve, reject2) => {
        let settled = false;
        let deadline;
        const finish = (error) => {
          if (settled) return;
          settled = true;
          deadlines.clearTimeout(deadline);
          if (error !== void 0) {
            reject2(error);
            return;
          }
          if (pending) {
            resolve(runNext());
            return;
          }
          resolve();
        };
        deadline = deadlines.setTimeout(() => {
          controller.abort();
          finish(new RenderDeadlineExceededError());
        }, options.deadlineMs);
        try {
          void renderOnce(controller.signal).then(
            () => finish(),
            (error) => finish(error)
          );
        } catch (error) {
          finish(error);
        }
      });
    };
    const request = () => {
      pending = true;
      if (active === null) {
        active = runNext().finally(() => {
          active = null;
        });
      }
      return active;
    };
    return Object.freeze({ request });
  }
  var browserIntervals = Object.freeze({
    setInterval: (callback, milliseconds2) => globalThis.setInterval(callback, milliseconds2),
    clearInterval: (handle) => globalThis.clearInterval(handle)
  });
  function createBackgroundRefresh(fetchOnce2, options) {
    if (!Number.isFinite(options.deadlineMs) || options.deadlineMs < 1) throw new Error("Fetch deadline must be positive");
    if (!Number.isFinite(options.intervalMs) || options.intervalMs < 1) throw new Error("Fetch interval must be positive");
    const deadlines = options.deadlines ?? browserDeadlines;
    const setInterval = options.setInterval ?? browserIntervals.setInterval;
    const clearInterval = options.clearInterval ?? browserIntervals.clearInterval;
    let disposed = false;
    let pending = false;
    let active = null;
    let currentController = null;
    const runFetch = () => {
      pending = false;
      const controller = new AbortController();
      currentController = controller;
      return new Promise((resolve) => {
        let settled = false;
        let deadline;
        const finish = () => {
          if (settled) return;
          settled = true;
          deadlines.clearTimeout(deadline);
          if (currentController === controller) currentController = null;
          if (!disposed && pending) {
            resolve(runFetch());
            return;
          }
          resolve();
        };
        deadline = deadlines.setTimeout(() => {
          controller.abort();
          finish();
        }, options.deadlineMs);
        const afterFetch = () => {
          if (disposed || controller.signal.aborted || options.onFetched === void 0) return;
          try {
            void options.onFetched();
          } catch {
          }
        };
        try {
          void fetchOnce2(controller.signal).then(
            () => {
              afterFetch();
              finish();
            },
            () => finish()
          );
        } catch {
          finish();
        }
      });
    };
    const request = () => {
      if (disposed) return Promise.resolve();
      pending = true;
      if (active === null) {
        active = runFetch().finally(() => {
          active = null;
        });
      }
      return active;
    };
    const intervalHandle = setInterval(() => {
      void request();
    }, options.intervalMs);
    return Object.freeze({
      request,
      dispose: () => {
        if (disposed) return;
        disposed = true;
        clearInterval(intervalHandle);
        currentController?.abort();
      }
    });
  }

  // src/production/operator-console/renderer/action-requests.ts
  var cameraActions = /* @__PURE__ */ new Set(["stream-start", "stream-stop", "photo-capture", "photo-fetch"]);
  var missionActions = /* @__PURE__ */ new Set(["mission-stage", "mission-upload", "mission-start", "mission-pause", "mission-resume", "mission-stop"]);
  function createActionRequests() {
    const devices = /* @__PURE__ */ new Map();
    const pending = (deviceId, action) => deviceId !== null && devices.get(deviceId)?.has(action) === true;
    const reason = (deviceId, action) => {
      if (deviceId === null) return null;
      const active = devices.get(deviceId);
      if (active === void 0) return null;
      if (active.has(action)) return "\u8FD9\u6761\u547D\u4EE4\u6B63\u5728\u5904\u7406\uFF0C\u8BF7\u7A0D\u5019";
      if (cameraActions.has(action) && action !== "stream-stop" && [...active.keys()].some((key) => cameraActions.has(key))) {
        return "\u76F8\u673A\u64CD\u4F5C\u6B63\u5728\u5904\u7406\uFF0C\u5B8C\u6210\u540E\u518D\u6267\u884C\u6B64\u64CD\u4F5C";
      }
      if (missionActions.has(action) && action !== "mission-pause" && action !== "mission-stop" && [...active.keys()].some((key) => missionActions.has(key))) {
        return "\u822A\u7EBF\u547D\u4EE4\u6B63\u5728\u5904\u7406\uFF0C\u5B8C\u6210\u540E\u518D\u6267\u884C\u6B64\u64CD\u4F5C";
      }
      return null;
    };
    const reserve = (deviceId, action) => {
      const blocked2 = reason(deviceId, action);
      if (blocked2 !== null) return Object.freeze({ ok: false, reason: blocked2 });
      if (deviceId === null) return Object.freeze({ ok: true, release: () => void 0 });
      const active = devices.get(deviceId) ?? /* @__PURE__ */ new Map();
      const token = Symbol(action);
      active.set(action, token);
      devices.set(deviceId, active);
      return Object.freeze({ ok: true, release: () => {
        if (active.get(action) !== token) return;
        active.delete(action);
        if (active.size === 0 && devices.get(deviceId) === active) devices.delete(deviceId);
      } });
    };
    return Object.freeze({ pending, reason, reserve });
  }

  // src/production/operator-console/renderer/playback-progress.ts
  function createPlaybackProgress() {
    let lastFrames = -1;
    let lastTime = -1;
    let lastProgressAt = 0;
    return Object.freeze({
      reset: () => {
        lastFrames = -1;
        lastTime = -1;
        lastProgressAt = 0;
      },
      observe: (now, time, frames) => {
        const changed = frames === null ? time > lastTime + 0.05 : frames > lastFrames;
        if (changed) lastProgressAt = now;
        lastFrames = frames ?? lastFrames;
        if (changed) lastTime = time;
        return lastProgressAt;
      }
    });
  }

  // src/production/operator-console/renderer/photo-progress.ts
  var stages = ["preparing", "listing", "downloading", "receiving", "verifying", "saving", "saved", "completed", "empty", "failed", "disconnected"];
  var read3 = (source, key) => {
    if (source === null || typeof source !== "object" || Array.isArray(source)) return void 0;
    try {
      return source[key];
    } catch {
      return void 0;
    }
  };
  var nonnegative = (value) => typeof value === "number" && Number.isFinite(value) && value >= 0 ? value : null;
  var integer = (value) => typeof value === "number" && Number.isSafeInteger(value) && value >= 0 ? value : null;
  var safeText = (value) => typeof value === "string" && value.trim().length > 0 && value.length <= 4096 && !/[\p{Cc}]/u.test(value) ? value : null;
  var stageOf = (value) => typeof value === "string" && stages.includes(value) ? value : null;
  var terminal = (stage) => stage === "completed" || stage === "empty" || stage === "failed" || stage === "disconnected";
  var stageText = (stage, index) => {
    const current = index > 0 ? `\u7B2C ${index} \u5F20\uFF1A` : "";
    switch (stage) {
      case "preparing":
        return "\u6B63\u5728\u68C0\u67E5\u7535\u8111\u5DF2\u6709\u7167\u7247";
      case "listing":
        return "\u6B63\u5728\u8BFB\u53D6\u98DE\u673A\u7167\u7247\u5217\u8868";
      case "downloading":
        return `${current}\u98DE\u673A \u2192 \u624B\u673A`;
      case "receiving":
        return `${current}\u624B\u673A \u2192 \u7535\u8111`;
      case "verifying":
        return `${current}\u6821\u9A8C\u539F\u56FE`;
      case "saving":
        return `${current}\u4FDD\u5B58\u539F\u56FE`;
      case "saved":
        return index > 0 ? `\u7B2C ${index} \u5F20\u5DF2\u4FDD\u5B58` : "\u539F\u56FE\u5DF2\u4FDD\u5B58";
      case "completed":
        return "\u56DE\u4F20\u5B8C\u6210";
      case "empty":
        return "\u98DE\u673A\u4E0A\u6CA1\u6709\u7535\u8111\u7F3A\u5C11\u7684\u7167\u7247";
      case "failed":
        return "\u56DE\u4F20\u5931\u8D25";
      case "disconnected":
        return "\u8FDE\u63A5\u5DF2\u65AD\u5F00\uFF0C\u56DE\u4F20\u4E2D\u65AD";
    }
  };
  var duration = (milliseconds2) => {
    const seconds = Math.floor(Math.max(0, milliseconds2) / 1e3);
    if (seconds < 60) return `${seconds} \u79D2`;
    const minutes = Math.floor(seconds / 60);
    return minutes < 60 ? `${minutes} \u5206 ${seconds % 60} \u79D2` : `${Math.floor(minutes / 60)} \u65F6 ${minutes % 60} \u5206 ${seconds % 60} \u79D2`;
  };
  var clockText = (milliseconds2) => new Date(milliseconds2).toLocaleTimeString("zh-CN", { hour12: false, hour: "2-digit", minute: "2-digit", second: "2-digit" });
  var bytesText = (bytes) => bytes < 1024 ? `${Math.round(bytes)} B` : bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  function createPhotoProgressCache() {
    const photos = /* @__PURE__ */ new Map();
    const observe = (devices, nowMs) => {
      const online = /* @__PURE__ */ new Set();
      for (const device of devices) {
        const deviceId = safeText(read3(device, "deviceId"));
        if (deviceId === null) continue;
        if (read3(read3(device, "connection"), "relay") !== "offline") online.add(deviceId);
        const photo = read3(device, "photo");
        const incoming = photoProgressView(photo, nowMs);
        if (incoming === null) continue;
        if (!online.has(deviceId) && incoming.active && photos.has(deviceId)) continue;
        const progress = read3(photo, "progress");
        const history = read3(progress, "history");
        photos.set(deviceId, {
          ...photo,
          progress: { ...progress, history: Array.isArray(history) ? history.map((event) => ({ ...event })) : [] }
        });
      }
      for (const [deviceId, photo] of photos) {
        if (online.has(deviceId)) continue;
        const progress = photo.progress;
        const stage = stageOf(progress.stage);
        if (stage === null || terminal(stage)) continue;
        const atMs = Math.max(nonnegative(progress.updatedAtMs) ?? nowMs, nowMs);
        const history = Array.isArray(progress.history) ? progress.history : [];
        const event = { stage: "disconnected", photoIndex: progress.photoIndex, completedCount: progress.completedCount, fileName: progress.fileName, atMs };
        photos.set(deviceId, {
          ...photo,
          code: "DISCONNECTED",
          progress: { ...progress, stage: "disconnected", failedStage: stage, updatedAtMs: atMs, finishedAtMs: atMs, history: [...history, event].slice(-8) }
        });
      }
    };
    return Object.freeze({ observe, get: (deviceId) => deviceId === null ? void 0 : photos.get(deviceId), deviceIds: () => [...photos.keys()] });
  }
  function photoProgressView(photo, nowMs) {
    const progress = read3(photo, "progress");
    const stage = stageOf(read3(progress, "stage"));
    const photoIndex = integer(read3(progress, "photoIndex"));
    const completedCount = integer(read3(progress, "completedCount"));
    const startedAtMs = nonnegative(read3(progress, "startedAtMs"));
    const updatedAtMs = nonnegative(read3(progress, "updatedAtMs"));
    if (stage === null || photoIndex === null || completedCount === null || startedAtMs === null || updatedAtMs === null) return null;
    const active = !terminal(stage);
    const finishedAtMs = nonnegative(read3(progress, "finishedAtMs"));
    const elapsedEnd = active ? nowMs : finishedAtMs ?? updatedAtMs;
    const age = Math.max(0, nowMs - updatedAtMs);
    const timing = active ? `\u5DF2\u7528\u65F6 ${duration(elapsedEnd - startedAtMs)} \xB7 ${age < 1e3 ? "\u521A\u521A\u66F4\u65B0" : `${duration(age)}\u524D\u66F4\u65B0`}` : `\u7528\u65F6 ${duration(elapsedEnd - startedAtMs)} \xB7 \u6700\u540E\u66F4\u65B0 ${clockText(updatedAtMs)}`;
    const historySource = read3(progress, "history");
    const events = Array.isArray(historySource) ? historySource.slice(-8) : [];
    const history = events.flatMap((event) => {
      const eventStage = stageOf(read3(event, "stage"));
      const index = integer(read3(event, "photoIndex"));
      const atMs = nonnegative(read3(event, "atMs"));
      if (eventStage === null || index === null || atMs === null) return [];
      const fileName = safeText(read3(event, "fileName"));
      return [`${clockText(atMs)} \xB7 ${stageText(eventStage, index)}${fileName === null ? "" : ` \xB7 ${fileName}`}`];
    });
    const failedStage = stageOf(read3(progress, "failedStage")) ?? events.map((event) => stageOf(read3(event, "stage"))).reverse().find((value) => value !== null && !terminal(value));
    const failure3 = stage === "failed" || stage === "disconnected";
    const heading = `${stageText(stage, photoIndex)}${failure3 && failedStage !== void 0 && failedStage !== null ? `\uFF08${stageText(failedStage, photoIndex)}\uFF09` : ""}`;
    const transferredBytes = nonnegative(read3(progress, "transferredBytes"));
    const totalBytes = nonnegative(read3(progress, "totalBytes"));
    const hasTransfer = (stage === "downloading" || stage === "receiving") && transferredBytes !== null && totalBytes !== null && totalBytes > 0;
    const percent = hasTransfer ? Math.floor(Math.min(100, transferredBytes / totalBytes * 100)) : null;
    const platformError = read3(progress, "platformError");
    const errorCode = safeText(read3(platformError, "code"));
    const description = safeText(read3(platformError, "description"));
    const code = safeText(read3(photo, "code"));
    const error = failure3 ? [code === null ? null : `\u56DE\u4F20\u7ED3\u679C\uFF1A${code}`, errorCode === null ? null : `\u9519\u8BEF\u7801\uFF1A${errorCode}`, description === null ? null : `\u9519\u8BEF\u8BF4\u660E\uFF1A${description}`].filter((value) => value !== null).join("\n") || null : null;
    return {
      stage,
      active,
      heading,
      count: `\u7D2F\u8BA1\u5DF2\u4FDD\u5B58 ${completedCount} \u5F20\u539F\u56FE`,
      fileName: safeText(read3(progress, "fileName")) ?? "\u5C1A\u672A\u53D6\u5F97\u6587\u4EF6\u540D",
      timing,
      transfer: hasTransfer ? `\u672C\u5F20\u672C\u9636\u6BB5 ${bytesText(transferredBytes)} / ${bytesText(totalBytes)} \xB7 ${percent}%` : null,
      percent,
      history,
      error,
      announcementKey: `${startedAtMs}:${stage}:${photoIndex}:${code ?? ""}:${heading}`
    };
  }
  var mounted = /* @__PURE__ */ new WeakMap();
  var setText = (node, value) => {
    if (node.textContent !== value) node.textContent = value;
  };
  var element = (tag, className) => {
    const node = document.createElement(tag);
    node.className = className;
    return node;
  };
  var mount = (host) => {
    host.classList.add("photo-batch-progress");
    host.setAttribute("aria-label", "\u7167\u7247\u56DE\u4F20\u8FDB\u5EA6");
    const title = element("h3", "photo-progress-title");
    title.textContent = "\u7167\u7247\u56DE\u4F20";
    const stage = element("p", "photo-progress-stage");
    stage.setAttribute("aria-live", "polite");
    stage.setAttribute("aria-atomic", "true");
    const count = element("p", "photo-progress-count");
    const fileName = element("p", "photo-progress-file");
    const timing = element("p", "photo-progress-timing");
    const transfer = element("p", "photo-progress-transfer");
    const meter = element("progress", "photo-progress-meter");
    meter.max = 100;
    meter.setAttribute("aria-label", "\u5F53\u524D\u7167\u7247\u5F53\u524D\u4F20\u8F93\u9636\u6BB5\u8FDB\u5EA6");
    const history = element("details", "photo-progress-history");
    history.open = true;
    const historySummary = element("summary", "");
    historySummary.textContent = "\u6700\u8FD1\u9636\u6BB5";
    const historyList = element("ol", "photo-progress-events");
    history.append(historySummary, historyList);
    const error = element("details", "photo-progress-error");
    const errorSummary = element("summary", "");
    errorSummary.textContent = "\u9519\u8BEF\u8BE6\u60C5";
    const errorText = element("p", "");
    error.append(errorSummary, errorText);
    host.replaceChildren(title, stage, count, fileName, timing, transfer, meter, history, error);
    const nodes = { stage, count, fileName, timing, transfer, meter, history, historyList, error, errorText, historyKey: "", announcementKey: "" };
    mounted.set(host, nodes);
    return nodes;
  };
  function renderPhotoProgress(host, view, idleMessage = "\u5C1A\u672A\u5F00\u59CB\u56DE\u4F20\u7167\u7247") {
    const nodes = mounted.get(host) ?? mount(host);
    host.dataset.photoStage = view?.stage ?? "idle";
    const announcementKey = view?.announcementKey ?? `idle:${idleMessage}`;
    if (nodes.announcementKey !== announcementKey) {
      setText(nodes.stage, view?.heading ?? idleMessage);
      nodes.announcementKey = announcementKey;
    }
    for (const node of [nodes.count, nodes.fileName, nodes.timing]) node.hidden = view === null;
    setText(nodes.count, view?.count ?? "");
    setText(nodes.fileName, view === null ? "" : `\u6587\u4EF6\uFF1A${view.fileName}`);
    setText(nodes.timing, view?.timing ?? "");
    nodes.transfer.hidden = view?.transfer === null || view === null;
    nodes.meter.hidden = view?.percent === null || view === null;
    setText(nodes.transfer, view?.transfer ?? "");
    if (view?.percent !== null && view?.percent !== void 0) nodes.meter.value = view.percent;
    nodes.history.hidden = view === null || view.history.length === 0;
    const historyKey = JSON.stringify(view?.history ?? []);
    if (nodes.historyKey !== historyKey) {
      nodes.historyList.replaceChildren(...(view?.history ?? []).map((item) => {
        const event = document.createElement("li");
        event.textContent = item;
        return event;
      }));
      nodes.historyKey = historyKey;
    }
    nodes.error.hidden = view?.error === null || view === null;
    setText(nodes.errorText, view?.error ?? "");
  }

  // src/production/operator-console/renderer/cesium-config.ts
  function createOrbitViewerOptions() {
    return {
      animation: false,
      timeline: false,
      geocoder: false,
      homeButton: false,
      sceneModePicker: false,
      baseLayerPicker: false,
      navigationHelpButton: false,
      fullscreenButton: false,
      infoBox: false,
      selectionIndicator: false,
      scene3DOnly: true,
      skyBox: false,
      skyAtmosphere: false,
      baseLayer: false,
      targetFrameRate: 30,
      msaaSamples: 1,
      requestRenderMode: true,
      maximumRenderTimeChange: Number.POSITIVE_INFINITY
    };
  }
  function attachWaylineImagery(Cesium, viewer2, tianDiTu) {
    viewer2.imageryLayers.removeAll();
    if (tianDiTu !== void 0) {
      viewer2.imageryLayers.addImageryProvider(new Cesium.UrlTemplateImageryProvider({
        url: tianDiTu.imageryUrl,
        credit: new Cesium.Credit("Map data (c) National Geomatics Center of China")
      }));
      viewer2.imageryLayers.addImageryProvider(new Cesium.UrlTemplateImageryProvider({
        url: tianDiTu.annotationUrl,
        credit: new Cesium.Credit("Map labels (c) National Geomatics Center of China")
      }));
      viewer2.scene.backgroundColor = Cesium.Color.fromCssColorString("#1d2e36");
      return "tianditu";
    }
    viewer2.imageryLayers.addImageryProvider(new Cesium.UrlTemplateImageryProvider({
      url: "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
      credit: new Cesium.Credit("Tiles (c) Esri")
    }));
    viewer2.scene.backgroundColor = Cesium.Color.fromCssColorString("#1d2e36");
    return "esri";
  }

  // src/production/operator-console/renderer/hangzhou-city-model.ts
  var hangzhouCityCameraView = {
    longitude: 120.16654968261719,
    latitude: 30.321480751037598,
    height: 4e3,
    heading: 0.45,
    pitch: -Math.PI / 4,
    roll: 0
  };
  function hangzhouTilesetUrl(baseUrl) {
    return new URL("city-tiles/hangzhou/tileset.json", baseUrl).href;
  }
  async function loadHangzhouCityModel(Cesium, viewer2, baseUrl = document.baseURI) {
    const tileset = await Cesium.Cesium3DTileset.fromUrl(hangzhouTilesetUrl(baseUrl), {
      show: false,
      maximumScreenSpaceError: 12,
      skipLevelOfDetail: true,
      preferLeaves: true,
      dynamicScreenSpaceError: true
    });
    viewer2.scene.primitives.add(tileset);
    return tileset;
  }
  var nanjingCityCameraView = {
    longitude: 118.778,
    latitude: 32.043,
    height: 4e3,
    heading: 0.45,
    pitch: -Math.PI / 4,
    roll: 0
  };
  function nanjingTilesetUrl(baseUrl) {
    return new URL("city-tiles/nanjing/tileset.json", baseUrl).href;
  }
  function setNanjingCamera(Cesium, viewer2) {
    viewer2.camera.setView({
      destination: Cesium.Cartesian3.fromDegrees(
        nanjingCityCameraView.longitude,
        nanjingCityCameraView.latitude,
        nanjingCityCameraView.height
      ),
      orientation: {
        heading: nanjingCityCameraView.heading,
        pitch: nanjingCityCameraView.pitch,
        roll: nanjingCityCameraView.roll
      }
    });
    viewer2.scene.requestRender();
  }
  async function loadNanjingCityModel(Cesium, viewer2, baseUrl = document.baseURI) {
    const tileset = await Cesium.Cesium3DTileset.fromUrl(nanjingTilesetUrl(baseUrl), {
      show: false,
      maximumScreenSpaceError: 12,
      skipLevelOfDetail: true,
      preferLeaves: true,
      dynamicScreenSpaceError: true
    });
    viewer2.scene.primitives.add(tileset);
    return tileset;
  }

  // src/production/operator-console/renderer/route-map.ts
  var cesium = () => {
    const value = globalThis.Cesium;
    if (value === void 0) throw new Error("Cesium \u672A\u52A0\u8F7D");
    return value;
  };
  var positionOf = (Cesium, point) => Cesium.Cartesian3.fromDegrees(point.longitude, point.latitude, point.altitude ?? 0);
  var viewer;
  var cityTilesets = [];
  var mapVisible = false;
  var cameraMoving = false;
  var mapIdleTimer;
  function wakeRouteMap() {
    if (viewer === void 0 || viewer.isDestroyed() || !viewer.useDefaultRenderLoop) return;
    viewer.targetFrameRate = 30;
    clearTimeout(mapIdleTimer);
    // Explicit rendering still runs scene updates; lower those while idle too.
    mapIdleTimer = setTimeout(() => {
      if (viewer === void 0 || viewer.isDestroyed()) return;
      cameraMoving = false;
      updateCityModelVisibility();
      viewer.targetFrameRate = 5;
    }, 1000);
  }
  function pauseRouteCityModels() {
    cameraMoving = true;
    updateCityModelVisibility();
    wakeRouteMap();
  }
  function updateCityModelVisibility() {
    if (viewer === void 0 || viewer.isDestroyed()) return;
    // These city datasets only have full-detail leaf geometry, with no coarse LOD.
    // Keep the overview light; Cesium frustum-culls the buildings after zooming in.
    const show = !cameraMoving && viewer.camera.positionCartographic.height < 2500;
    let changed = false;
    for (const tileset of cityTilesets) {
      if (tileset.show === show) continue;
      tileset.show = show;
      changed = true;
    }
    if (changed) viewer.scene.requestRender();
  }
  var drawnRouteId = null;
  var drawnPositions = [];
  var notice = "\u5BFC\u5165 Wayline \u5BFC\u51FA\u7684 KML \u6216 KMZ \u6587\u4EF6\u4EE5\u9884\u89C8\u822A\u8FF9\u3002";
  var routeMapNotice = () => notice;
  async function ensureRouteMap(host) {
    if (viewer !== void 0 && !viewer.isDestroyed()) {
      viewer.resize();
      return;
    }
    const Cesium = cesium();
    Cesium.Ion.defaultAccessToken = "";
    viewer = new Cesium.Viewer(host, createOrbitViewerOptions());
    // The first visibility transition must resize the host and request a frame.
    viewer.useDefaultRenderLoop = false;
    const provider = attachWaylineImagery(Cesium, viewer);
    viewer.scene.globe.depthTestAgainstTerrain = true;
    viewer.scene.screenSpaceCameraController.minimumZoomDistance = 20;
    viewer.scene.screenSpaceCameraController.enableTilt = true;
    setNanjingCamera(Cesium, viewer);
    cityTilesets = [];
    cameraMoving = false;
    // Collision correction also emits camera events; only input/flight wakes the map.
    viewer.camera.moveEnd.addEventListener(() => {
      const wasMoving = cameraMoving;
      cameraMoving = false;
      updateCityModelVisibility();
      if (wasMoving) wakeRouteMap();
    });
    viewer.scene.postRender.addEventListener(() => {
      if (cameraMoving) wakeRouteMap();
    });
    for (const event of ["pointerdown", "wheel", "keydown", "touchstart"]) {
      host.addEventListener(event, pauseRouteCityModels, { passive: true });
    }
    document.addEventListener("visibilitychange", () => setRouteMapVisible(mapVisible));
    const imagery = provider === "tianditu" ? "\u5929\u5730\u56FE\u5F71\u50CF" : "Esri \u536B\u661F\u56FE";
    // Optional building metadata must not delay route drawing or map interaction.
    void loadRouteCityModels(Cesium, viewer, imagery);
  }
  async function loadRouteCityModels(Cesium, routeViewer, imagery) {
    const loaded = [];
    const failed = [];
    try {
      cityTilesets.push(await loadNanjingCityModel(Cesium, routeViewer));
      updateCityModelVisibility();
      loaded.push("\u5357\u4EAC\u4E09\u7EF4\u767D\u6A21");
    } catch (error) {
      failed.push(`\u5357\u4EAC\u4E09\u7EF4\u767D\u6A21\u52A0\u8F7D\u5931\u8D25\uFF1A${error instanceof Error ? error.message : String(error)}`);
    }
    if (routeViewer.isDestroyed()) return;
    try {
      cityTilesets.push(await loadHangzhouCityModel(Cesium, routeViewer));
      updateCityModelVisibility();
      loaded.push("\u676D\u5DDE\u4E09\u7EF4\u767D\u6A21");
    } catch (error) {
      failed.push(`\u676D\u5DDE\u4E09\u7EF4\u767D\u6A21\u52A0\u8F7D\u5931\u8D25\uFF1A${error instanceof Error ? error.message : String(error)}`);
    }
    if (routeViewer.isDestroyed()) return;
    if (loaded.length > 0) {
      notice = `${loaded.join("\u3001")}\u5DF2\u52A0\u8F7D\uFF0C\u5F53\u524D\u5E95\u56FE\uFF1A${imagery}\u3002\u5BFC\u5165 Wayline \u5BFC\u51FA\u7684 KML \u6216 KMZ \u6587\u4EF6\u4EE5\u53E0\u52A0\u822A\u8FF9\u3002`;
    } else {
      notice = failed.join("\uFF1B") || `\u57CE\u5E02\u767D\u6A21\u52A0\u8F7D\u5931\u8D25\uFF0C\u5F53\u524D\u5E95\u56FE\uFF1A${imagery}\u3002`;
    }
  }
  function setRouteMapVisible(visible) {
    mapVisible = visible;
    if (viewer === void 0 || viewer.isDestroyed()) return;
    visible = visible && !document.hidden;
    if (viewer.useDefaultRenderLoop === visible) return;
    viewer.useDefaultRenderLoop = visible;
    if (visible) {
      viewer.resize();
      viewer.scene.requestRender();
      wakeRouteMap();
    } else {
      clearTimeout(mapIdleTimer);
    }
  }
  function showRoutePreview(routeId, preview, fly = true) {
    const Cesium = cesium();
    if (viewer === void 0 || viewer.isDestroyed()) return;
    const routeViewer = viewer;
    viewer.entities.removeAll();
    drawnRouteId = routeId;
    const positions = preview.polyline.map((point) => positionOf(Cesium, point));
    drawnPositions = positions;
    if (positions.length < 2) return;
    viewer.entities.add({
      name: "\u5BFC\u5165\u822A\u7EBF",
      polyline: {
        positions,
        width: 5,
        material: Cesium.Color.fromCssColorString("#d7f16a"),
        clampToGround: false
      }
    });
    preview.polyline.slice(1, -1).forEach((point, index) => {
      routeViewer.entities.add({
        name: `\u822A\u70B9${index + 2}`,
        position: positionOf(Cesium, point),
        point: { pixelSize: 8, color: Cesium.Color.fromCssColorString("#52d6ff"), outlineColor: Cesium.Color.BLACK, outlineWidth: 1 }
      });
    });
    viewer.entities.add({
      name: "\u8D77\u70B9",
      position: positionOf(Cesium, preview.startMarker),
      point: { pixelSize: 12, color: Cesium.Color.fromCssColorString("#d7f16a"), outlineColor: Cesium.Color.BLACK, outlineWidth: 2 },
      label: {
        text: "\u8D77\u70B9",
        font: "12px sans-serif",
        fillColor: Cesium.Color.WHITE,
        outlineColor: Cesium.Color.BLACK,
        outlineWidth: 2,
        style: Cesium.LabelStyle.FILL_AND_OUTLINE,
        verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
        pixelOffset: new Cesium.Cartesian2(0, -14)
      }
    });
    viewer.entities.add({
      name: "\u7EC8\u70B9",
      position: positionOf(Cesium, preview.endMarker),
      point: { pixelSize: 12, color: Cesium.Color.fromCssColorString("#ff9f43"), outlineColor: Cesium.Color.BLACK, outlineWidth: 2 },
      label: {
        text: "\u7EC8\u70B9",
        font: "12px sans-serif",
        fillColor: Cesium.Color.WHITE,
        outlineColor: Cesium.Color.BLACK,
        outlineWidth: 2,
        style: Cesium.LabelStyle.FILL_AND_OUTLINE,
        verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
        pixelOffset: new Cesium.Cartesian2(0, -14)
      }
    });
    if (fly) {
      pauseRouteCityModels();
      viewer.camera.flyToBoundingSphere(Cesium.BoundingSphere.fromPoints(positions), {
        duration: 0.8,
        offset: new Cesium.HeadingPitchRange(0, -Math.PI / 2.8, 0)
      });
    }
    notice = `${preview.polyline.length} \u4E2A\u822A\u70B9`;
  }
  function locateDrawnRoute() {
    if (viewer === void 0 || viewer.isDestroyed() || drawnPositions.length < 2) return;
    const Cesium = cesium();
    pauseRouteCityModels();
    viewer.camera.flyToBoundingSphere(Cesium.BoundingSphere.fromPoints(drawnPositions), {
      duration: 0.8,
      offset: new Cesium.HeadingPitchRange(0, -Math.PI / 2.8, 0)
    });
  }
  function clearRoutePreview() {
    drawnRouteId = null;
    drawnPositions = [];
    if (viewer === void 0 || viewer.isDestroyed()) return;
    viewer.entities.removeAll();
  }
  function drawnPreviewId() {
    return drawnRouteId;
  }

  // src/production/operator-console/renderer/main.ts
  var requestedWorkspace = new URLSearchParams(globalThis.location?.search ?? "").get("workspace");
  var state = {
    workspace: requestedWorkspace === "routes" || requestedWorkspace === "flight" || requestedWorkspace === "monitor" || requestedWorkspace === "devices" ? requestedWorkspace : "devices",
    flightPanel: "stream",
    missionDeviceId: null,
    streamDeviceId: null
  };
  var lastSnapshot = {};
  var lastRelayHint = "";
  var lastRoutePreview = null;
  var lastDeviceDetailHtml = null;
  var lastPlaybackIdentity = "off";
  var photoProgressCache = createPhotoProgressCache();
  var photoProgressDeviceId = null;
  var bridge = () => {
    const api = window.skyCommand;
    if (api === void 0 || typeof api.invoke !== "function") throw new Error("\u6E32\u67D3\u8FDB\u7A0B\u53EA\u80FD\u901A\u8FC7 skyCommand.invoke \u8BBF\u95EE\u7F51\u5173");
    return api;
  };
  var renderAbortError = () => {
    const error = new Error("Renderer redraw was superseded");
    error.name = "AbortError";
    return error;
  };
  var awaitCurrentRender = (operation, signal) => {
    if (signal === void 0) return operation;
    if (signal.aborted) return Promise.reject(renderAbortError());
    return new Promise((resolve, reject2) => {
      const abort = () => {
        cleanup();
        reject2(renderAbortError());
      };
      const cleanup = () => signal.removeEventListener("abort", abort);
      signal.addEventListener("abort", abort, { once: true });
      void operation.then(
        (value) => {
          cleanup();
          signal.aborted ? reject2(renderAbortError()) : resolve(value);
        },
        (error) => {
          cleanup();
          reject2(error);
        }
      );
    });
  };
  var unwrap2 = (result) => {
    if (result === null || typeof result !== "object" || !("ok" in result) || !("value" in result) || result.ok !== true) return result;
    return result.value;
  };
  var unwrapAll = (result) => {
    let current = result;
    for (let step = 0; step < 4; step += 1) {
      const next = unwrap2(current);
      if (next === current) return current;
      current = next;
    }
    return current;
  };
  var safeRenderInvoke = async (name, input, signal) => {
    try {
      return await awaitCurrentRender(bridge().invoke(name, input), signal);
    } catch {
      return null;
    }
  };
  var read4 = (value, key) => value !== null && typeof value === "object" && !Array.isArray(value) ? value[key] : void 0;
  var text4 = (value) => typeof value === "string" && value.trim().length > 0 ? value : null;
  var selectedFlightDevice = (view, deviceId) => view.devices.find(
    (device) => device !== null && typeof device === "object" && !Array.isArray(device) && read4(device, "deviceId") === deviceId
  );
  var connectionOf = (device) => device === void 0 ? null : read4(device, "connection");
  var relayStatus = (device) => {
    if (device === void 0) return "\u672A\u9009\u62E9\u624B\u673A";
    const value = read4(connectionOf(device), "relay");
    return value === "online" ? "\u5728\u7EBF" : value === "offline" ? "\u79BB\u7EBF" : "\u72B6\u6001\u672A\u77E5";
  };
  var msdkStatus = (device) => {
    if (device === void 0) return "\u672A\u9009\u62E9\u624B\u673A";
    const value = read4(connectionOf(device), "msdk");
    if (value === "ready") return "\u5DF2\u5C31\u7EEA";
    if (value === "starting") return "\u521D\u59CB\u5316\u4E2D";
    if (value === "failed") return "\u521D\u59CB\u5316\u5931\u8D25";
    if (value === "stopped") return "\u5DF2\u505C\u6B62";
    return "\u72B6\u6001\u672A\u77E5";
  };
  var linkStatus = (device, field) => {
    if (device === void 0) return "\u672A\u9009\u62E9\u624B\u673A";
    const value = read4(connectionOf(device), field);
    return value === "connected" ? "\u5DF2\u8FDE\u63A5" : value === "disconnected" ? "\u5DF2\u65AD\u5F00" : "\u72B6\u6001\u672A\u77E5";
  };
  var booleanStatus = (device, field, positive, negative) => {
    if (device === void 0) return "\u672A\u9009\u62E9\u624B\u673A";
    const value = read4(connectionOf(device), field);
    return value === true ? positive : value === false ? negative : "\u5C1A\u672A\u53D6\u5F97";
  };
  var enumStatus = (device, field) => {
    if (device === void 0) return "\u672A\u9009\u62E9\u624B\u673A";
    return text4(read4(connectionOf(device), field)) ?? "\u5C1A\u672A\u53D6\u5F97";
  };
  var cameraFrameFact = (source) => read4(source, "cameraFrames");
  var cameraFrameStatus = (source) => {
    switch (read4(cameraFrameFact(source), "state")) {
      case "unavailable":
        return "\u5F53\u524D\u672A\u542F\u7528\u5E27\u89C2\u5BDF\uFF08UNAVAILABLE\uFF09";
      case "unobserved":
        return "\u5DF2\u76D1\u542C\uFF0C\u5C1A\u672A\u6536\u5230\u76F8\u673A\u7F16\u7801\u5E27\uFF08UNOBSERVED\uFF09";
      case "receiving":
        return "\u6B63\u5728\u6536\u5230\u76F8\u673A\u7F16\u7801\u5E27\uFF08RECEIVING\uFF09";
      case "stalled":
        return "\u66FE\u6536\u5230\u5E27\uFF0C\u5F53\u524D\u672A\u89C1\u65B0\u5E27\uFF08STALLED\uFF09";
      default:
        return "\u624B\u673A\u7AEF\u5C1A\u672A\u63D0\u4F9B\u5E27\u89C2\u5BDF";
    }
  };
  var cameraFrameGenerationStatus = (source) => {
    const generation = finiteNumber(read4(cameraFrameFact(source), "generation"));
    return generation === null || !Number.isSafeInteger(generation) || generation < 0 ? "\u5C1A\u672A\u53D6\u5F97" : String(generation);
  };
  var cameraFrameCountStatus = (source) => {
    const count = finiteNumber(read4(cameraFrameFact(source), "receivedFrameCount"));
    return count === null || !Number.isSafeInteger(count) || count < 0 ? "\u5C1A\u672A\u6536\u5230\u6709\u6548\u5E27" : `${count} \u5E27`;
  };
  var cameraFrameAgeStatus = (source) => {
    const age = finiteNumber(read4(cameraFrameFact(source), "lastFrameAgeMillis"));
    return age === null || !Number.isSafeInteger(age) || age < 0 ? "\u5C1A\u672A\u53D6\u5F97" : `${age} ms`;
  };
  var cameraFrameFormatStatus = (source) => {
    const frames = cameraFrameFact(source);
    const codec = optionalText(read4(frames, "codec"));
    const width = finiteNumber(read4(frames, "width"));
    const height = finiteNumber(read4(frames, "height"));
    const frameRate = finiteNumber(read4(frames, "frameRate"));
    const format = width === null || height === null ? null : `${width} x ${height}`;
    const rate = frameRate === null ? null : `${frameRate} fps`;
    const parts = [codec, format, rate].filter((part) => part !== null);
    return parts.length === 0 ? "\u5C1A\u672A\u53D6\u5F97" : parts.join(" \xB7 ");
  };
  var missionExecutionLabels = Object.freeze({
    NOT_STARTED: "\u624B\u673A\u5DF2\u6682\u5B58\uFF0C\u5C1A\u672A\u542F\u52A8",
    STARTING: "\u624B\u673A\u4EFB\u52A1\u6B63\u5728\u542F\u52A8",
    EXECUTING: "\u624B\u673A\u62A5\u544A\u6267\u884C\u4E2D",
    PAUSED: "\u624B\u673A\u62A5\u544A\u5DF2\u6682\u505C",
    STOPPING: "\u624B\u673A\u4EFB\u52A1\u6B63\u5728\u505C\u6B62",
    FINISHED: "\u624B\u673A\u62A5\u544A\u5DF2\u7ED3\u675F",
    FAILED: "\u624B\u673A\u62A5\u544A\u6267\u884C\u5931\u8D25"
  });
  var missionDjiExecutionLabels = Object.freeze({
    IDLE: "DJI \u62A5\u544A\u7A7A\u95F2",
    READY: "DJI \u62A5\u544A\u5C31\u7EEA",
    UPLOADING: "DJI \u6B63\u5728\u4E0A\u4F20\u822A\u7EBF",
    PREPARING: "DJI \u6B63\u5728\u51C6\u5907\u822A\u7EBF",
    RECOVERING: "DJI \u6B63\u5728\u6062\u590D\u4EFB\u52A1",
    ENTER_WAYLINE: "DJI \u62A5\u544A\u8FDB\u5165\u9996\u822A\u70B9",
    EXECUTING: "DJI \u62A5\u544A\u6267\u884C\u4E2D",
    PAUSED: "DJI \u62A5\u544A\u5DF2\u6682\u505C",
    INTERRUPTED: "DJI \u62A5\u544A\u5DF2\u4E2D\u65AD",
    FINISHED: "DJI \u62A5\u544A\u5DF2\u5B8C\u6210",
    RETURN_TO_START_POINT: "DJI \u6B63\u5728\u8FD4\u56DE\u8D77\u70B9",
    DISCONNECTED: "DJI \u62A5\u544A\u6267\u884C\u94FE\u8DEF\u65AD\u5F00",
    NOT_SUPPORTED: "\u5F53\u524D\u8BBE\u5907\u4E0D\u652F\u6301\u822A\u7EBF\u6267\u884C",
    UNKNOWN: "DJI \u8FD4\u56DE\u672A\u77E5\u6267\u884C\u72B6\u6001"
  });
  var missionDjiExecutionStatus = (device) => {
    if (device === void 0) return "\u672A\u9009\u62E9\u624B\u673A";
    const value = text4(read4(connectionOf(device), "missionDjiExecutionState"));
    if (value === null) return "\u5F53\u524D\u4EFB\u52A1\u6682\u65E0 DJI \u6267\u884C\u89C2\u5BDF";
    return missionDjiExecutionLabels[value] === void 0 ? `DJI \u8FD4\u56DE\u672A\u8BC6\u522B\u6267\u884C\u72B6\u6001\uFF08${value}\uFF09` : `${missionDjiExecutionLabels[value]}\uFF08${value}\uFF09`;
  };
  var missionWaypointIndexStatus = (device) => {
    if (device === void 0) return "\u672A\u9009\u62E9\u624B\u673A";
    const value = read4(connectionOf(device), "currentWaypointIndex");
    return typeof value === "number" && Number.isSafeInteger(value) && value >= 0 ? `\u822A\u70B9 ${value}` : "\u5C1A\u672A\u53D6\u5F97";
  };
  var missionWaylineIdStatus = (device) => {
    if (device === void 0) return "\u672A\u9009\u62E9\u624B\u673A";
    const value = read4(connectionOf(device), "waylineId");
    return typeof value === "number" && Number.isSafeInteger(value) && value >= 0 ? String(value) : "\u5C1A\u672A\u53D6\u5F97";
  };
  var missionExecutingFileStatus = (device) => {
    if (device === void 0) return "\u672A\u9009\u62E9\u624B\u673A";
    return text4(read4(connectionOf(device), "waylineExecutingMissionFileName")) ?? "\u5C1A\u672A\u53D6\u5F97";
  };
  var missionWaypointActionStatus = (device) => {
    if (device === void 0) return "\u672A\u9009\u62E9\u624B\u673A";
    const connection = connectionOf(device);
    const phase = text4(read4(connection, "waypointActionPhase"));
    const actionId = read4(connection, "waypointActionId");
    if (phase === null || typeof actionId !== "number" || !Number.isSafeInteger(actionId) || actionId < 0) return "\u5C1A\u672A\u53D6\u5F97";
    const group = read4(connection, "waypointActionGroup");
    const groupText = typeof group === "number" && Number.isSafeInteger(group) && group >= 0 ? `\u52A8\u4F5C\u7EC4 ${group} / ` : "";
    const phaseText = phase === "START" ? "\u8FDB\u884C\u4E2D" : phase === "FINISH" ? "\u5DF2\u7ED3\u675F" : phase;
    const errorCode = text4(read4(connection, "waypointActionErrorCode"));
    const errorDescription = text4(read4(connection, "waypointActionErrorDescription"));
    const errorText = errorCode !== null ? `\uFF1B${errorCode}${errorDescription !== null ? ` ${errorDescription}` : ""}` : "";
    return `${groupText}\u52A8\u4F5C ${actionId} \xB7 ${phaseText}${errorText}`;
  };
  var missionWaylineInterruptStatus = (device) => {
    if (device === void 0) return "\u672A\u9009\u62E9\u624B\u673A";
    const code = text4(read4(connectionOf(device), "waylineInterruptErrorCode"));
    const description = text4(read4(connectionOf(device), "waylineInterruptErrorDescription"));
    if (code === null && description === null) return "\u5C1A\u672A\u53D6\u5F97";
    return [code, description].filter((part) => part !== null).join(" ");
  };
  var knownStatus = (value) => value === "\u5C1A\u672A\u53D6\u5F97" || value === "\u672A\u9009\u62E9\u624B\u673A" || value === "\u672A\u62A5\u544A\u4E0A\u4F20" || value === "\u5F53\u524D\u4EFB\u52A1\u6682\u65E0 DJI \u6267\u884C\u89C2\u5BDF" ? null : value;
  var renderFlightStatus = (name, value) => {
    const node = document.querySelector(`[data-flight-status="${name}"]`);
    if (node instanceof HTMLElement) node.textContent = value;
  };
  var renderFlightHud = (name, value) => {
    const node = document.querySelector(`[data-flight-hud="${name}"]`);
    if (node instanceof HTMLElement) node.textContent = value;
  };
  function renderStreamFlightStatus(streamDevice) {
    const streamConnection = connectionOf(streamDevice);
    const live = read4(streamConnection, "live");
    const streaming = read4(live, "streaming");
    const player = document.getElementById("video");
    const painting = streamDevice !== void 0 && player instanceof HTMLVideoElement && isPainting(player);
    renderFlightStatus("stream-reach", commandReachStatus({
      selected: streamDevice !== void 0,
      relayOnline: relayStatus(streamDevice) === "\u5728\u7EBF",
      msdkReady: msdkStatus(streamDevice) === "\u5DF2\u5C31\u7EEA",
      airLinkConnected: linkStatus(streamDevice, "airLink") === "\u5DF2\u8FDE\u63A5",
      cameraConnected: linkStatus(streamDevice, "camera") === "\u5DF2\u8FDE\u63A5"
    }));
    renderFlightStatus("stream-push", streamPushStatus({
      selected: streamDevice !== void 0,
      streaming: streaming === true ? true : streaming === false ? false : null,
      resolution: text4(read4(live, "resolution")),
      fps: typeof read4(live, "fps") === "number" || typeof read4(live, "fps") === "string" ? read4(live, "fps") : null
    }));
    renderFlightStatus("stream-paint", streamPaintStatus({ selected: streamDevice !== void 0, painting }));
    const runtimeError = read4(live, "runtimeError");
    renderFlightStatus("stream-msdk-runtime-error", streamErrorStatus({
      selected: streamDevice !== void 0,
      code: text4(read4(runtimeError, "code")),
      description: text4(read4(runtimeError, "description"))
    }));
  }
  function renderMissionFlightStatus(missionDevice) {
    const missionConnection = connectionOf(missionDevice);
    renderFlightStatus("mission-reach", commandReachStatus({
      selected: missionDevice !== void 0,
      relayOnline: relayStatus(missionDevice) === "\u5728\u7EBF",
      msdkReady: msdkStatus(missionDevice) === "\u5DF2\u5C31\u7EEA"
    }));
    renderFlightStatus("mission-execution-now", missionExecutionNowStatus({
      selected: missionDevice !== void 0,
      execution: knownStatus(missionDjiExecutionStatus(missionDevice)),
      waypoint: knownStatus(missionWaypointIndexStatus(missionDevice)),
      fileName: knownStatus(missionExecutingFileStatus(missionDevice))
    }));
    const uploadRaw = read4(missionConnection, "missionUploadProgress");
    renderFlightStatus("mission-upload-or-action", missionUploadOrActionStatus({
      selected: missionDevice !== void 0,
      uploadPercent: typeof uploadRaw === "number" && Number.isSafeInteger(uploadRaw) ? uploadRaw : null,
      action: knownStatus(missionWaypointActionStatus(missionDevice))
    }));
    renderFlightStatus("mission-wayline-interrupt", interruptStatus({
      selected: missionDevice !== void 0,
      code: text4(read4(missionConnection, "waylineInterruptErrorCode")),
      description: text4(read4(missionConnection, "waylineInterruptErrorDescription"))
    }));
  }
  function renderDirectFlightStatus(missionDevice) {
    const directConnection = connectionOf(missionDevice);
    const flying = text4(read4(directConnection, "flightState"));
    const motorsOn = read4(directConnection, "motorsOn");
    const directRthState = read4(directConnection, "lowBatteryRthState");
    renderFlightStatus("direct-reach", commandReachStatus({
      selected: missionDevice !== void 0,
      relayOnline: relayStatus(missionDevice) === "\u5728\u7EBF",
      msdkReady: msdkStatus(missionDevice) === "\u5DF2\u5C31\u7EEA"
    }));
    renderFlightStatus("direct-process", directProcessStatus({
      selected: missionDevice !== void 0,
      flying,
      motorsOn: motorsOn === true ? true : motorsOn === false ? false : null,
      flightMode: text4(read4(directConnection, "flightMode")),
      landingConfirmationNeeded: read4(directConnection, "landingConfirmationNeeded") === true,
      lowBatteryRthState: text4(directRthState)
    }));
    renderFlightStatus("direct-landing-protection", enumStatus(missionDevice, "landingProtectionState") === "\u5C1A\u672A\u53D6\u5F97" ? "\u2014" : enumStatus(missionDevice, "landingProtectionState"));
    renderFlightStatus("direct-landing-confirmation", booleanStatus(missionDevice, "landingConfirmationNeeded", "\u9700\u8981\u786E\u8BA4", "\u4E0D\u9700\u8981"));
    const rthLabel = directRthState === "UNKNOWN" ? "\u672A\u77E5\uFF08MSDK \u8FD4\u56DE UNKNOWN\uFF09" : lowBatteryRthLabel(directRthState);
    const rthRemain = directRthState === "UNKNOWN" ? null : durationLabel(read4(directConnection, "remainingFlightTimeSeconds"));
    renderFlightStatus("direct-low-battery-rth", rthLabel === null ? "\u2014" : rthRemain === null ? rthLabel : `${rthLabel} \xB7 ${rthRemain}`);
    renderFlightStatus("direct-alert", directAlertStatus({
      selected: missionDevice !== void 0,
      takeoffFailure: knownStatus(enumStatus(missionDevice, "takeoffFailureError")),
      motorStartFailure: knownStatus(enumStatus(missionDevice, "motorStartFailureError")),
      visionWarning: knownStatus(enumStatus(missionDevice, "visionSystemWarning"))
    }));
    const pose = read4(directConnection, "pose");
    renderFlightHud("flying", hudFlying(flying));
    renderFlightHud("motors", hudMotors(motorsOn === true ? true : motorsOn === false ? false : null));
    renderFlightHud("battery", hudBattery(read4(directConnection, "batteryPercent")));
    renderFlightHud("altitude", hudAltitude(read4(pose, "altitudeMeters")));
    renderFlightHud("gps", hudText(read4(directConnection, "gpsSignalLevel")));
    renderFlightHud("mode", hudText(read4(directConnection, "flightMode")));
  }
  function renderFlightPanelStatus(view) {
    const streamDevice = selectedFlightDevice(view, view.streamDeviceId);
    const missionDevice = selectedFlightDevice(view, view.missionDeviceId);
    renderStreamFlightStatus(streamDevice);
    renderMissionFlightStatus(missionDevice);
    renderDirectFlightStatus(missionDevice);
  }
  function renderFlightPanelVisibility() {
    document.querySelectorAll("[data-flight-panel]").forEach((button) => {
      const active = button.dataset.flightPanel === state.flightPanel;
      button.classList.toggle("active", active);
      button.setAttribute("aria-selected", active ? "true" : "false");
    });
    document.querySelectorAll("[data-flight-panel-view]").forEach((panel) => {
      panel.hidden = panel.dataset.flightPanelView !== state.flightPanel;
    });
  }
  var renderFlightConfirmationFallback = () => {
    pendingFlightConfirmation = adoptPendingConfirmation(pendingFlightConfirmation, null, Date.now());
  };
  var renderMissionStartConfirmationFallback = () => {
  };
  var clearArmTimer = () => {
    if (armExpiryTimer !== null) {
      window.clearTimeout(armExpiryTimer);
      armExpiryTimer = null;
    }
  };
  var scheduleArmExpiry = () => {
    clearArmTimer();
    if (armedCommand === null) return;
    const remain = ARM_HOLD_MS - (Date.now() - armedCommand.armedAtMs);
    armExpiryTimer = window.setTimeout(() => {
      void expireArmedCommand();
    }, Math.max(0, remain));
  };
  var cancelArmedBackend = async () => {
    if (armedCommand === null) return;
    const confirmation = pendingFlightConfirmation;
    if (confirmation === null) return;
    pendingFlightConfirmation = null;
    await run("flight-cancel", "flight-cancel", { deviceId: confirmation.deviceId, confirmationId: confirmation.confirmationId }, void 0, `flight-${confirmation.action}`);
  };
  var expireArmedCommand = async () => {
    if (armedCommand === null) return;
    await cancelArmedBackend();
    armedCommand = null;
    clearArmTimer();
    await render();
  };
  var feedbackByAction = /* @__PURE__ */ new Map();
  var actionRequests = createActionRequests();
  var el = (id) => {
    const node = document.getElementById(id);
    if (node === null) throw new Error(`\u7F3A\u5C11 ${id}`);
    return node;
  };
  var show = (message) => {
    el("status").textContent = message;
  };
  var showPlaybackHealth = (message) => {
    const hint = el("playback-health");
    hint.textContent = message;
    hint.hidden = message.length === 0;
  };
  var feedbackForDevice = (action, deviceId, connectionEpoch, feedback) => {
    feedbackByAction.set(action, Object.freeze({ deviceId, connectionEpoch, feedback }));
  };
  var feedbackDeviceEpoch = (view, deviceId) => {
    if (deviceId === null) return null;
    const device = view.devices.find((item) => read4(item, "deviceId") === deviceId);
    const epoch = read4(device, "connectionEpoch");
    return typeof epoch === "number" && Number.isSafeInteger(epoch) && epoch >= 0 ? epoch : null;
  };
  var captureFeedback = (action, deviceId, connectionEpoch, value) => {
    const feedback = operationFeedback(action, value);
    feedbackForDevice(action === "pairing-start" || action === "pairing-stop" ? "pairing" : action, deviceId, connectionEpoch, feedback);
    return feedback;
  };
  var renderOperationFeedback = (action, deviceId, connectionEpoch) => {
    const node = document.querySelector(`[data-operation-feedback="${action}"]`);
    if (!(node instanceof HTMLElement)) return;
    const record3 = feedbackByAction.get(action);
    const visible = record3 !== void 0 && record3.deviceId === deviceId && (deviceId === null || record3.connectionEpoch === connectionEpoch);
    node.textContent = visible ? record3.feedback.message : "";
    if (visible) {
      node.dataset.feedbackSource = record3.feedback.source;
      node.dataset.feedbackOutcome = record3.feedback.outcome;
    } else {
      delete node.dataset.feedbackSource;
      delete node.dataset.feedbackOutcome;
    }
  };
  var flightActionLabel = (action) => {
    if (action === "takeoff") return "\u8D77\u98DE";
    if (action === "land") return "\u964D\u843D";
    if (action === "confirm-landing") return "\u786E\u8BA4\u7EE7\u7EED\u964D\u843D";
    if (action === "return-home" || action === "returnHome" || action === "rth") return "\u8FD4\u822A";
    if (action === "stop-takeoff") return "\u505C\u6B62\u81EA\u52A8\u8D77\u98DE";
    if (action === "stop-auto-landing") return "\u505C\u6B62\u81EA\u52A8\u964D\u843D";
    if (action === "stop-go-home") return "\u9000\u51FA\u8FD4\u822A";
    return typeof action === "string" && action.length > 0 ? action : "\u8BE5\u52A8\u4F5C";
  };
  var operatorNotice = (value) => {
    const inner = unwrap2(value);
    const codeOf = (source) => typeof read4(source, "code") === "string" ? read4(source, "code") : null;
    const reasonOf = (source) => typeof read4(source, "reason") === "string" ? read4(source, "reason") : null;
    const reason = reasonOf(inner) ?? reasonOf(value);
    const code = codeOf(inner) ?? codeOf(value);
    const platformError = read4(inner, "platformError") ?? read4(value, "platformError");
    if (code === "HARDWARE_NOT_READY") {
      const blockers = read4(inner, "blockers") ?? read4(value, "blockers");
      if (Array.isArray(blockers)) {
        const messages = blockers.map((item) => read4(item, "message")).filter((message) => typeof message === "string" && message.length > 0);
        if (messages.length > 0) return messages.join("\uFF1B");
      }
      return "\u7535\u8111\u56FE\u4F20\u63A5\u6536\u6761\u4EF6\u672A\u6EE1\u8DB3";
    }
    if (reason === "ANOTHER_VIDEO_TRANSPORT_ACTIVE") return "\u53E6\u4E00\u8DEF\u56FE\u4F20\u6B63\u5728\u4F7F\u7528\uFF0C\u8BF7\u5148\u505C\u6B62";
    if (reason === "VIDEO_TRANSPORT_FAILED") return "\u56FE\u4F20\u672A\u80FD\u5B8C\u6210";
    if (reason === "VIDEO_TRANSPORT_UNAVAILABLE") return "\u56FE\u4F20\u5F53\u524D\u4E0D\u53EF\u7528";
    if (code === "FLIGHT_ACTION_REJECTED") {
      const djiCode = text4(read4(platformError, "code"));
      const description = text4(read4(platformError, "description"));
      return description === null ? "DJI \u62D2\u7EDD\u4E86\u8BE5\u98DE\u884C\u547D\u4EE4" : `DJI \u62D2\u7EDD${flightActionLabel(read4(inner, "action"))}\uFF1A${description}${djiCode === null ? "" : `\uFF08${djiCode}\uFF09`}`;
    }
    if (code === "RESULT_UNCONFIRMED") return "\u672A\u6536\u5230 DJI \u672C\u6B21\u547D\u4EE4\u7684\u6700\u7EC8\u7ED3\u679C\uFF1B\u8BF7\u4EE5\u9065\u63A7\u5668\u548C\u98DE\u884C\u5668\u5B9E\u9645\u72B6\u6001\u4E3A\u51C6";
    if (code === "FLIGHT_ACTION_INVOCATION_FAILED") return "\u624B\u673A\u5728\u53D6\u5F97 DJI \u547D\u4EE4\u7ED3\u679C\u524D\u53D1\u751F\u9519\u8BEF\uFF1B\u672C\u6B21\u547D\u4EE4\u672A\u83B7\u786E\u8BA4";
    if (code === "RELAY_REJECTED") return "\u624B\u673A\u62D2\u7EDD\u4E86\u8BE5\u547D\u4EE4\uFF0C\u8BF7\u5728\u624B\u673A\u4E0A\u770B\u539F\u56E0\u540E\u91CD\u8BD5";
    if (read4(inner, "ok") === true && read4(inner, "action") === "land") return "DJI \u5DF2\u63A5\u53D7\u81EA\u52A8\u964D\u843D\uFF0C\u6B63\u5728\u7B49\u5F85 MSDK \u56DE\u62A5\u843D\u5730\u6216\u7EE7\u7EED\u786E\u8BA4";
    if (code === "CAPABILITY_BLOCKED") {
      if (reason === "RELAY_OFFLINE") return "\u624B\u673A\u5DF2\u79BB\u7EBF\uFF0C\u65E0\u6CD5\u53D1\u9001\u56FE\u4F20\u547D\u4EE4";
      if (reason === "SDK_NOT_READY") return "\u624B\u673A\u7AEF DJI \u5C1A\u672A\u5C31\u7EEA\uFF0C\u65E0\u6CD5\u542F\u52A8\u56FE\u4F20";
      if (reason === "AIRLINK_OFFLINE") return "AirLink \u672A\u8FDE\u63A5\uFF0C\u672A\u8C03\u7528 DJI MSDK \u542F\u52A8\u56FE\u4F20";
      if (reason === "AIRLINK_CONNECTION_UNKNOWN") return "AirLink \u72B6\u6001\u672A\u77E5\uFF0C\u672A\u8C03\u7528 DJI MSDK \u542F\u52A8\u56FE\u4F20";
      if (reason === "CAMERA_OFFLINE") return "\u4E3B\u76F8\u673A\u672A\u8FDE\u63A5\uFF0C\u672A\u8C03\u7528 DJI MSDK \u542F\u52A8\u56FE\u4F20";
      if (reason === "CAMERA_CONNECTION_UNKNOWN") return "\u4E3B\u76F8\u673A\u72B6\u6001\u672A\u77E5\uFF0C\u672A\u8C03\u7528 DJI MSDK \u542F\u52A8\u56FE\u4F20";
      return "\u56FE\u4F20\u547D\u4EE4\u6B64\u523B\u4E0D\u53EF\u8FBE\uFF0C\u8BF7\u786E\u8BA4\u624B\u673A\u4E2D\u7EE7\u4E0E MSDK \u72B6\u6001\u540E\u91CD\u8BD5";
    }
    if (code === "OPERATION_IN_PROGRESS") return "\u4E0A\u4E00\u6761\u547D\u4EE4\u8FD8\u5728\u5904\u7406\uFF0C\u8BF7\u7A0D\u5019";
    if (code === "VIDEO_NOT_READY") return "\u753B\u9762\u8FD8\u6CA1\u51FA\u6765\uFF0C\u8BF7\u7A0D\u5019\u6216\u91CD\u65B0\u542F\u52A8\u56FE\u4F20";
    if (code === "STATUS_REFRESH_FAILED") return "\u672C\u6B21\u72B6\u6001\u5FEB\u7167\u672A\u901A\u8FC7\u9A8C\u8BC1\uFF0C\u8BF7\u786E\u8BA4\u624B\u673A\u4ECD\u5728\u7EBF\u540E\u91CD\u8BD5";
    if (code === "DISCONNECTED") return "\u624B\u673A\u5DF2\u79BB\u7EBF\uFF0C\u8BF7\u5148\u5728\u8BBE\u5907\u9875\u8FDE\u4E0A\u624B\u673A";
    if (code === "DEPENDENCY_FAILURE") return "\u6682\u65F6\u65E0\u6CD5\u5B8C\u6210\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5";
    if (read4(inner, "ok") === true) return "\u5DF2\u53D1\u9001\u5230\u624B\u673A";
    if (read4(value, "ok") === true && read4(inner, "ok") !== false) return "\u5DF2\u53D1\u9001\u5230\u624B\u673A";
    return code === null ? "\u5DF2\u53D1\u9001\u5230\u624B\u673A" : "\u6682\u65F6\u65E0\u6CD5\u5B8C\u6210\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5";
  };
  var flvPlayer = null;
  var detachingFlightVideo = false;
  var flightPlaybackEpoch = 0;
  var desiredFlightPlayback = "off";
  var flightPlaybackRequest = null;
  var monitorPlaybackEpoch = 0;
  var attachedUrl = null;
  var flvFatalStreak = 0;
  var flvRecoverTimer = null;
  var lastPlaybackHealthAt = 0;
  var attachedAtMs = 0;
  var lastPaintAtMs = 0;
  var lastSeenCurrentTime = 0;
  var firstPlaybackFrameReported = false;
  var flightPlaybackProgress = createPlaybackProgress();
  var selectedPlaybackDeviceId = null;
  var pendingFlightConfirmation = null;
  var armedCommand = null;
  var armExpiryTimer = null;
  var videoPlayRetryTimer = null;
  var videoPlayEventsBoundTo = null;
  var playbackWatchTimer = null;
  var lastLiveEdgeSeekAtMs = 0;
  var phoneLinkProbes = /* @__PURE__ */ new Map();
  var phoneLinkProbeInFlightDeviceId = null;
  var pairingCommandInFlight = null;
  var NO_FRAME_MS = 25e3;
  var STALL_MS = 12e3;
  var LIVE_EDGE_MAX_LAG_S = 8;
  var LIVE_EDGE_KEEP_S = 0.5;
  var PLAYBACK_WATCH_MS = 2e3;
  var LIVE_EDGE_SEEK_COOLDOWN_MS = 2e3;
  var LIVE_BACKWARD_MAX_S = 8;
  var LIVE_BACKWARD_KEEP_S = 3;
  var LIVE_PLAYER_CONFIG = {
    enableStashBuffer: true,
    stashInitialSize: 262144,
    lazyLoad: false,
    liveSync: true,
    liveSyncMaxLatency: 1,
    liveSyncTargetLatency: 0.5,
    liveSyncPlaybackRate: 1.08,
    autoCleanupSourceBuffer: true,
    autoCleanupMaxBackwardDuration: LIVE_BACKWARD_MAX_S,
    autoCleanupMinBackwardDuration: LIVE_BACKWARD_KEEP_S
  };
  var clearFlvRecoverTimer = () => {
    if (flvRecoverTimer === null) return;
    window.clearTimeout(flvRecoverTimer);
    flvRecoverTimer = null;
  };
  var isPainting = (video) => video.videoWidth > 0 && !video.paused && Number.isFinite(video.currentTime) && video.currentTime > 0 && (typeof video.getVideoPlaybackQuality !== "function" || video.getVideoPlaybackQuality().totalVideoFrames > 1);
  var clearVideoPlayRetry = () => {
    if (videoPlayRetryTimer === null) return;
    window.clearTimeout(videoPlayRetryTimer);
    videoPlayRetryTimer = null;
  };
  var scheduleVideoPlay = (video) => {
    clearVideoPlayRetry();
    videoPlayRetryTimer = window.setTimeout(() => {
      videoPlayRetryTimer = null;
      if (flvPlayer === null || attachedUrl === null) return;
      if (!video.paused && video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA) return;
      void video.play().catch((error) => {
        const name = error instanceof Error ? error.name : "";
        if (name === "NotAllowedError") show("\u81EA\u52A8\u64AD\u653E\u88AB\u62E6\u622A\uFF0C\u8BF7\u70B9\u4E00\u4E0B\u4E0A\u65B9\u753B\u9762");
      });
    }, 0);
  };
  var bindVideoPlayEvents = (video) => {
    if (videoPlayEventsBoundTo === video) return;
    const hooked = video;
    const onReady = () => {
      scheduleVideoPlay(video);
    };
    const onLiveEdge = () => {
      chaseLiveEdge(video);
    };
    for (const event of ["loadedmetadata", "loadeddata", "canplay", "playing"]) {
      video.addEventListener(event, onReady);
    }
    video.addEventListener("timeupdate", onLiveEdge);
    videoPlayEventsBoundTo = video;
    hooked.__skyCommandPlayReady = onReady;
    hooked.__skyCommandLiveEdge = onLiveEdge;
  };
  var unbindVideoPlayEvents = (video) => {
    const hooked = video;
    const onReady = hooked.__skyCommandPlayReady;
    const onLiveEdge = hooked.__skyCommandLiveEdge;
    if (onReady === void 0 && onLiveEdge === void 0) return;
    for (const event of ["loadedmetadata", "loadeddata", "canplay", "playing"]) {
      if (onReady !== void 0) video.removeEventListener(event, onReady);
    }
    if (onLiveEdge !== void 0) video.removeEventListener("timeupdate", onLiveEdge);
    delete hooked.__skyCommandPlayReady;
    delete hooked.__skyCommandLiveEdge;
    if (videoPlayEventsBoundTo === video) videoPlayEventsBoundTo = null;
  };
  var detachVideo = () => {
    detachingFlightVideo = true;
    try {
      stopPlaybackWatch();
      clearFlvRecoverTimer();
      clearVideoPlayRetry();
      const video = el("video");
      unbindVideoPlayEvents(video);
      if (flvPlayer !== null) {
        try {
          flvPlayer.pause();
        } catch {
        }
        try {
          flvPlayer.unload();
        } catch {
        }
        try {
          flvPlayer.detachMediaElement();
        } catch {
        }
        try {
          flvPlayer.destroy();
        } catch {
        }
        flvPlayer = null;
      }
      attachedUrl = null;
      lastPlaybackIdentity = "off";
      attachedAtMs = 0;
      lastPaintAtMs = 0;
      lastSeenCurrentTime = 0;
      flightPlaybackProgress.reset();
      firstPlaybackFrameReported = false;
      lastLiveEdgeSeekAtMs = 0;
      selectedPlaybackDeviceId = null;
      video.playbackRate = 1;
      video.removeAttribute("src");
      video.srcObject = null;
      video.load();
      showPlaybackHealth("");
    } finally {
      detachingFlightVideo = false;
    }
  };
  var playVideo = (video) => {
    video.muted = true;
    video.defaultMuted = true;
    video.volume = 0;
    video.setAttribute("playsinline", "");
    scheduleVideoPlay(video);
  };
  var softReloadFlv = (video) => {
    const url = attachedUrl;
    if (url === null) return false;
    try {
      detachVideo();
      attachVideo(url);
      playVideo(video);
      return flvPlayer !== null;
    } catch {
      return false;
    }
  };
  var startPlaybackWatch = (video) => {
    stopPlaybackWatch();
    playbackWatchTimer = window.setInterval(() => {
      watchPlaybackStall(video);
      reportPlaybackHealth(video);
    }, PLAYBACK_WATCH_MS);
  };
  var stopPlaybackWatch = () => {
    if (playbackWatchTimer === null) return;
    window.clearInterval(playbackWatchTimer);
    playbackWatchTimer = null;
  };
  var chaseLiveEdge = (video) => {
    if (flvPlayer === null || attachedUrl === null || !isPainting(video) || video.seeking) return;
    try {
      if (video.buffered.length === 0) return;
      const end = video.buffered.end(video.buffered.length - 1);
      if (!Number.isFinite(end)) return;
      const lag = end - video.currentTime;
      if (lag <= LIVE_EDGE_MAX_LAG_S) return;
      const now = Date.now();
      if (now - lastLiveEdgeSeekAtMs < LIVE_EDGE_SEEK_COOLDOWN_MS) return;
      lastLiveEdgeSeekAtMs = now;
      video.currentTime = Math.max(0, end - LIVE_EDGE_KEEP_S);
    } catch {
    }
  };
  var scheduleFlvReattach = (retryUrl) => {
    clearFlvRecoverTimer();
    if (state.workspace === "monitor" || detachingFlightVideo) return;
    const delayMs = Math.min(5e3, 800 * 2 ** Math.min(flvFatalStreak - 1, 3));
    flvRecoverTimer = window.setTimeout(() => {
      flvRecoverTimer = null;
      attachVideo(retryUrl);
    }, delayMs);
  };
  var recoverStuckFlv = (video, url, reason) => {
    if (flvRecoverTimer !== null) return;
    flvFatalStreak += 1;
    if (flvFatalStreak <= 3 && softReloadFlv(video)) {
      showPlaybackHealth(`${reason}\uFF0C\u6B63\u5728\u81EA\u52A8\u6062\u590D\u2026`);
      return;
    }
    detachVideo();
    showPlaybackHealth(`${reason}\uFF0C\u6B63\u5728\u91CD\u65B0\u8FDE\u63A5\u2026`);
    scheduleFlvReattach(url);
  };
  var recordFirstPlaybackFrame = (video) => {
    if (firstPlaybackFrameReported || video.videoWidth <= 0 || video.videoHeight <= 0) return;
    firstPlaybackFrameReported = true;
    void safeRenderInvoke("diagnostics-record", {
      action: "video-first-frame-rendered",
      reason: `width=${video.videoWidth};height=${video.videoHeight}`
    });
  };
  var notePaintProgress = (video) => {
    if (!isPainting(video)) return;
    recordFirstPlaybackFrame(video);
    const current = video.currentTime;
    const now = Date.now();
    const frames = typeof video.getVideoPlaybackQuality === "function" ? video.getVideoPlaybackQuality().totalVideoFrames : null;
    const progressAt = flightPlaybackProgress.observe(now, current, frames);
    if (progressAt !== lastPaintAtMs) {
      lastPaintAtMs = progressAt;
      lastSeenCurrentTime = current;
      flvFatalStreak = 0;
    }
  };
  var watchPlaybackStall = (video) => {
    if (attachedUrl === null || flvPlayer === null || flvRecoverTimer !== null) return;
    const now = Date.now();
    notePaintProgress(video);
    if (isPainting(video)) {
      if (lastPaintAtMs > 0 && now - lastPaintAtMs > STALL_MS) {
        recoverStuckFlv(video, attachedUrl, "\u753B\u9762\u505C\u4F4F");
      }
      return;
    }
    if (attachedAtMs > 0 && now - attachedAtMs > NO_FRAME_MS) {
      recoverStuckFlv(video, attachedUrl, "\u957F\u65F6\u95F4\u672A\u51FA\u753B");
    }
  };
  var reportPlaybackHealth = (video) => {
    const now = Date.now();
    if (now - lastPlaybackHealthAt < 2e3) return;
    lastPlaybackHealthAt = now;
    notePaintProgress(video);
    if (isPainting(video)) {
      if (lastPaintAtMs > 0 && now - lastPaintAtMs > STALL_MS) {
        showPlaybackHealth("\u56FE\u4F20\u753B\u9762\u505C\u4F4F\uFF0C\u6B63\u5728\u81EA\u52A8\u6062\u590D\u2026");
        return;
      }
      showPlaybackHealth(video.videoWidth > 0 ? `\u56FE\u4F20\u6B63\u5E38\u64AD\u653E\u4E2D\uFF08${video.videoWidth}\xD7${video.videoHeight}\uFF09` : "\u56FE\u4F20\u6B63\u5E38\u64AD\u653E\u4E2D");
      return;
    }
    if (attachedUrl !== null && flvPlayer !== null) {
      showPlaybackHealth("\u56FE\u4F20\u5DF2\u542F\u52A8\uFF0C\u6B63\u5728\u7B49\u5F85\u753B\u9762\u2026\u82E5\u957F\u65F6\u95F4\u65E0\u753B\u9762\u8BF7\u70B9\u300C\u505C\u6B62\u56FE\u4F20\u300D\u540E\u91CD\u8BD5");
    }
  };
  var attachVideo = (url) => {
    const video = el("video");
    if (attachedUrl === url && flvPlayer !== null) {
      if (isPainting(video)) {
        notePaintProgress(video);
        reportPlaybackHealth(video);
        return;
      }
      if (video.paused) playVideo(video);
      watchPlaybackStall(video);
      reportPlaybackHealth(video);
      return;
    }
    clearFlvRecoverTimer();
    detachVideo();
    attachedUrl = url;
    attachedAtMs = Date.now();
    lastPaintAtMs = 0;
    lastSeenCurrentTime = 0;
    const play = () => {
      playVideo(video);
      reportPlaybackHealth(video);
    };
    try {
      if (!url.includes(".flv") || !import_mpegts.default.isSupported()) {
        detachVideo();
        showPlaybackHealth("\u5F53\u524D\u7535\u8111\u65E0\u6CD5\u64AD\u653E\u56FE\u4F20\u753B\u9762\uFF0C\u8BF7\u91CD\u542F Sky Command \u540E\u518D\u8BD5");
        return;
      }
      flvPlayer = import_mpegts.default.createPlayer(
        { type: "flv", isLive: true, hasAudio: false, hasVideo: true, url },
        // 小 stash 缓毛刺/网络抖动；过大则延迟明显。背压策略已在 HTTP-FLV 侧按关键frame 续写。
        LIVE_PLAYER_CONFIG
      );
      bindVideoPlayEvents(video);
      flvPlayer.on(import_mpegts.default.Events.MEDIA_INFO, (info) => {
        scheduleVideoPlay(video);
        const record3 = info !== null && typeof info === "object" ? info : null;
        const width = typeof record3?.width === "number" ? record3.width : 0;
        const height = typeof record3?.height === "number" ? record3.height : 0;
        if (width > 0) showPlaybackHealth(`\u5DF2\u6536\u5230\u89C6\u9891\u4FE1\u606F\uFF08${width}\xD7${height}\uFF09\uFF0C\u6B63\u5728\u7B49\u5F85\u753B\u9762\u8FDE\u7EED\u64AD\u653E`);
      });
      flvPlayer.on(import_mpegts.default.Events.ERROR, (errorType, errorDetail) => {
        if (detachingFlightVideo || state.workspace === "monitor") return;
        flvFatalStreak += 1;
        const retryUrl = attachedUrl;
        console.warn("\u56FE\u4F20\u64AD\u653E\u8FDE\u63A5\u5F02\u5E38", errorType, errorDetail);
        if (retryUrl === null) return;
        detachVideo();
        showPlaybackHealth("\u56FE\u4F20\u64AD\u653E\u8FDE\u63A5\u5C1A\u672A\u5C31\u7EEA\uFF0C\u6B63\u5728\u81EA\u52A8\u91CD\u8FDE\u2026");
        scheduleFlvReattach(retryUrl);
      });
      flvPlayer.attachMediaElement(video);
      flvPlayer.load();
      play();
      startPlaybackWatch(video);
    } catch {
      flvFatalStreak += 1;
      detachVideo();
      showPlaybackHealth("\u56FE\u4F20\u64AD\u653E\u5668\u521D\u59CB\u5316\u5931\u8D25\uFF0C\u6B63\u5728\u81EA\u52A8\u91CD\u8BD5\u2026");
      scheduleFlvReattach(url);
    }
  };
  var accepted = (value) => value !== null && typeof value === "object" && value.ok === true;
  var confirmationFromResult = (value, fallbackDeviceId, fallbackAction) => {
    const body = unwrapAll(value);
    const confirmation = read4(body, "confirmation");
    const confirmationId = text4(read4(confirmation, "confirmationId"));
    const deviceId = text4(read4(confirmation, "deviceId")) ?? fallbackDeviceId;
    const action = text4(read4(confirmation, "action")) ?? text4(read4(body, "action")) ?? fallbackAction;
    if (confirmationId === null || deviceId === null || action === null) return null;
    const expiresAt = read4(confirmation, "expiresAtMs");
    const expiresAtMs = typeof expiresAt === "number" && Number.isFinite(expiresAt) ? expiresAt : Date.now() + 15e3;
    return Object.freeze({ deviceId, action, confirmationId, expiresAtMs });
  };
  var playbackUrl = (value) => {
    const body = unwrap2(value);
    if (body !== null && typeof body === "object" && read4(body, "ok") === false) return null;
    const url = read4(body, "url");
    if (typeof url === "string" && url.trim().length > 0) return url;
    const nested = unwrap2(body);
    const nestedUrl = read4(nested, "url");
    return typeof nestedUrl === "string" && nestedUrl.trim().length > 0 ? nestedUrl : null;
  };
  async function ensurePlayback(view, signal) {
    if (state.workspace === "monitor" || !view.playbackReady || view.streamDeviceId === null || actionRequests.pending(view.streamDeviceId, "stream-stop")) {
      if (desiredFlightPlayback !== "off") flightPlaybackEpoch += 1;
      desiredFlightPlayback = "off";
      flightPlaybackRequest = null;
      lastPlaybackIdentity = "off";
      if (attachedUrl !== null) detachVideo();
      return;
    }
    const identity = `on:${view.streamDeviceId}`;
    if (identity !== desiredFlightPlayback) {
      desiredFlightPlayback = identity;
      flightPlaybackEpoch += 1;
      flightPlaybackRequest = null;
    }
    if (flightPlaybackRequest?.identity === identity || flvRecoverTimer !== null) return;
    if (identity === lastPlaybackIdentity && attachedUrl !== null && flvPlayer !== null) return;
    const request = { identity, epoch: flightPlaybackEpoch };
    const epoch = request.epoch;
    flightPlaybackRequest = request;
    signal ??= AbortSignal.timeout(5e3);
    try {
      const playbackResult = await safeRenderInvoke("video-playback", { deviceId: view.streamDeviceId }, signal);
      if (playbackResult === null || epoch !== flightPlaybackEpoch || state.workspace === "monitor") return;
      const url = playbackUrl(playbackResult);
      if (url === null) return;
      attachVideo(url);
      if (epoch !== flightPlaybackEpoch || state.workspace === "monitor") {
        detachVideo();
        return;
      }
      if (flvPlayer === null || attachedUrl !== url) return;
      if (selectedPlaybackDeviceId !== view.streamDeviceId) {
        const selected = unwrap2(await safeRenderInvoke("stream-select", { deviceId: view.streamDeviceId }, signal));
        if (!accepted(selected) || epoch !== flightPlaybackEpoch || state.workspace === "monitor") return;
        selectedPlaybackDeviceId = view.streamDeviceId;
      }
      lastPlaybackIdentity = identity;
      watchPlaybackStall(el("video"));
    } finally {
      if (flightPlaybackRequest === request) flightPlaybackRequest = null;
    }
  }
  var monitorPlayers = /* @__PURE__ */ new Map();
  var monitorRetryAt = /* @__PURE__ */ new Map();
  var monitorStreamBusy = /* @__PURE__ */ new Set();
  var monitorStreamHoldCleared = /* @__PURE__ */ new Set();
  var monitorHoldBusy = /* @__PURE__ */ new Set();
  var monitorHomeBusy = /* @__PURE__ */ new Set();
  var monitorLandBusy = /* @__PURE__ */ new Set();
  var monitorRouteActionBusy = /* @__PURE__ */ new Set();
  var monitorPhotoBusy = /* @__PURE__ */ new Set();
  var monitorPhotoProgress = /* @__PURE__ */ new Map();
  var monitorHoldProgress = /* @__PURE__ */ new Map();
  var monitorDirectProgress = /* @__PURE__ */ new Map();
  var monitorStreamProgress = /* @__PURE__ */ new Map();
  var monitorMissionProgress = /* @__PURE__ */ new Map();
  var monitorRailKind = "route";
  var monitorFlightControllerHints = /* @__PURE__ */ new Map();
  var monitorViewMode = "grid";
  var monitorSingleSlot = 0;
  var monitorRoutePlans = /* @__PURE__ */ new Map();
  var monitorRailDeviceId = null;
  var monitorRouteStepText = Object.freeze({
    pending: "\u7B49\u5F85",
    running: "\u8FDB\u884C\u4E2D",
    done: "\u5DF2\u5B8C\u6210",
    failed: "\u5931\u8D25"
  });
  var monitorRoutePlan = (deviceId) => {
    const existing = monitorRoutePlans.get(deviceId);
    if (existing !== void 0) return existing;
    const created = {
      routeId: null,
      rail: "pick",
      resumeStatus: false,
      resumePhoto: false,
      resumeHold: false,
      resumeDirect: false,
      resumeStream: false,
      button: "choose",
      phone: "pending",
      aircraft: "pending",
      execute: "pending",
      failure: null
    };
    monitorRoutePlans.set(deviceId, created);
    return created;
  };
  var monitorDevices = (view) => view.devices.filter(
    (device) => device !== null && typeof device === "object" && !Array.isArray(device) && typeof read4(device, "deviceId") === "string"
  );
  var monitorVideoElement = (slot) => {
    const node = document.querySelector(`[data-monitor-video="${slot}"]`);
    return node instanceof HTMLVideoElement ? node : null;
  };
  var releaseMonitorSlot = (slot, cooldownMs = 0) => {
    const lane = monitorPlayers.get(slot);
    if (lane !== void 0) {
      monitorPlayers.delete(slot);
      if (lane.watchTimer !== null) window.clearInterval(lane.watchTimer);
      try {
        lane.player.pause();
      } catch {
      }
      try {
        lane.player.unload();
      } catch {
      }
      try {
        lane.player.detachMediaElement();
      } catch {
      }
      try {
        lane.player.destroy();
      } catch {
      }
    }
    const video = monitorVideoElement(slot);
    if (video !== null) {
      video.removeAttribute("src");
      video.srcObject = null;
      try {
        video.load();
      } catch {
      }
    }
    if (cooldownMs > 0) monitorRetryAt.set(slot, Date.now() + cooldownMs);
  };
  var releaseMonitorPlayers = () => {
    for (const slot of [0, 1, 2, 3]) releaseMonitorSlot(slot);
  };
  var attachMonitorPlayer = (slot, deviceId, url) => {
    const existing = monitorPlayers.get(slot);
    if (existing !== void 0 && existing.deviceId === deviceId && existing.url === url) {
      const current = monitorVideoElement(slot);
      if (current !== null && current.paused) void current.play().catch(() => void 0);
      return;
    }
    releaseMonitorSlot(slot);
    const video = monitorVideoElement(slot);
    if (video === null || !url.includes(".flv") || !import_mpegts.default.isSupported()) return;
    const player = import_mpegts.default.createPlayer(
      { type: "flv", isLive: true, hasAudio: false, hasVideo: true, url },
      LIVE_PLAYER_CONFIG
    );
    const lane = {
      deviceId,
      url,
      player,
      attachedAtMs: Date.now(),
      lastPaintAtMs: 0,
      lastSeenCurrentTime: 0,
      watchTimer: null
    };
    monitorPlayers.set(slot, lane);
    video.muted = true;
    video.defaultMuted = true;
    video.volume = 0;
    video.setAttribute("playsinline", "");
    const play = () => {
      void video.play().catch(() => void 0);
    };
    player.on(import_mpegts.default.Events.MEDIA_INFO, () => {
      play();
    });
    player.on(import_mpegts.default.Events.ERROR, () => {
      if (monitorPlayers.get(slot) !== lane) return;
      releaseMonitorSlot(slot, 1500);
    });
    player.attachMediaElement(video);
    player.load();
    play();
    lane.watchTimer = window.setInterval(() => {
      if (monitorPlayers.get(slot) !== lane || state.workspace !== "monitor") return;
      if (isPainting(video)) {
        if (lane.lastPaintAtMs === 0 || Math.abs(video.currentTime - lane.lastSeenCurrentTime) >= 0.05) {
          lane.lastPaintAtMs = Date.now();
          lane.lastSeenCurrentTime = video.currentTime;
        }
        if (lane.lastPaintAtMs > 0 && Date.now() - lane.lastPaintAtMs > STALL_MS) releaseMonitorSlot(slot, 1e3);
        return;
      }
      if (Date.now() - lane.attachedAtMs > NO_FRAME_MS) releaseMonitorSlot(slot, 1e3);
    }, PLAYBACK_WATCH_MS);
  };
  async function ensureMonitorPlayback(view) {
    const epoch = ++monitorPlaybackEpoch;
    flightPlaybackEpoch += 1;
    desiredFlightPlayback = "off";
    flightPlaybackRequest = null;
    if (attachedUrl !== null) detachVideo();
    const devices = monitorDevices(view);
    await Promise.all([0, 1, 2, 3].map(async (slot) => {
      if (monitorViewMode === "single" && slot !== monitorSingleSlot) {
        releaseMonitorSlot(slot);
        return;
      }
      const device = devices[slot];
      const deviceId = device === void 0 ? null : text4(read4(device, "deviceId"));
      const videoPhase = text4(read4(read4(device, "video"), "phase"));
      const streamPhase = text4(read4(read4(device, "stream"), "phase"));
      const unavailable = streamPhase === "failed" && text4(read4(read4(device, "stream"), "failureCode")) === "SOURCE_UNAVAILABLE";
      if (deviceId === null || videoPhase !== "ready" || unavailable) {
        releaseMonitorSlot(slot);
        return;
      }
      if ((monitorRetryAt.get(slot) ?? 0) > Date.now()) return;
      const existing = monitorPlayers.get(slot);
      if (existing !== void 0 && existing.deviceId === deviceId) return;
      const playbackResult = await safeRenderInvoke("video-playback", { deviceId });
      if (epoch !== monitorPlaybackEpoch || state.workspace !== "monitor") return;
      const url = playbackUrl(playbackResult);
      if (url === null) {
        releaseMonitorSlot(slot);
        return;
      }
      attachMonitorPlayer(slot, deviceId, url);
    }));
  }
  var connectionLabel = (connection, key, ok, disconnected, unknownLabel) => {
    const value = read4(connection, key);
    if (value === "ready" || value === "connected" || value === "online") return ok;
    if (value === "disconnected") return disconnected;
    return unknownLabel;
  };
  var connected = (connection, key) => {
    const value = read4(connection, key);
    return value === "ready" || value === "connected" || value === "online";
  };
  var msdkFact = (connection) => {
    switch (read4(connection, "msdk")) {
      case "ready":
        return { label: "MSDK \u5DF2\u5C31\u7EEA", ok: true };
      case "starting":
        return { label: "MSDK \u6B63\u5728\u521D\u59CB\u5316", ok: false };
      case "failed":
        return { label: "MSDK \u521D\u59CB\u5316\u5931\u8D25", ok: false };
      case "stopped":
        return { label: "MSDK \u5DF2\u505C\u6B62", ok: false };
      default:
        return { label: "MSDK \u72B6\u6001\u672A\u77E5", ok: false };
    }
  };
  var pairingControl = (view) => {
    const device = view.devices.find((item) => read4(item, "deviceId") === view.missionDeviceId);
    const pairingState = read4(read4(device, "connection"), "pairingState");
    return { action: pairingState === "PAIRING" || pairingState === "STOPPING" ? "pairing-stop" : "pairing-start", stopping: pairingState === "STOPPING" };
  };
  var pairingFact = (connection) => {
    switch (read4(connection, "pairingState")) {
      case "PAIRED":
        return { label: "\u5DF2\u5BF9\u9891", ok: true };
      case "PAIRING":
        return { label: "\u5BF9\u9891\u4E2D", ok: false };
      case "STOPPING":
        return { label: "\u6B63\u5728\u7ED3\u675F\u5BF9\u9891", ok: false };
      case "FAILED":
        return { label: "\u5BF9\u9891\u5931\u8D25", ok: false };
      case "IDLE":
        return { label: "\u672A\u5BF9\u9891", ok: false };
      case "UNKNOWN":
        return { label: "\u5BF9\u9891\u72B6\u6001\u672A\u77E5", ok: false };
      default:
        return { label: "\u5BF9\u9891\u72B6\u6001\u672A\u77E5", ok: false };
    }
  };
  var escapeHtml = (value) => value.replace(/[&<>"']/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[character]);
  var CALL_SIGN_STORAGE_KEY = "sky-command.call-signs";
  var readCallSignNumbers = () => {
    try {
      const parsed = JSON.parse(localStorage.getItem(CALL_SIGN_STORAGE_KEY) ?? "{}");
      if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) return {};
      const numbers = {};
      for (const [id, value] of Object.entries(parsed)) {
        if (typeof value === "number" && Number.isInteger(value) && value > 0) numbers[id] = value;
      }
      return numbers;
    } catch {
      return {};
    }
  };
  var callSign = (deviceId) => {
    const id = deviceId.trim();
    const numbers = { ...readCallSignNumbers() };
    let number = numbers[id];
    if (number === void 0) {
      const used = new Set(Object.values(numbers));
      number = 1;
      while (used.has(number)) number += 1;
      numbers[id] = number;
      try {
        localStorage.setItem(CALL_SIGN_STORAGE_KEY, JSON.stringify(numbers));
      } catch {
      }
    }
    return `\u91C7\u96C6 ${number}`;
  };
  var statusRow = (name, label, ok) => `<div class="connection-status-row"><span class="connection-status-name">${escapeHtml(name)}</span><span class="connection-status-value${ok ? " ok" : ""}">${escapeHtml(label)}</span></div>`;
  var finiteNumber = (value) => typeof value === "number" && Number.isFinite(value) ? value : null;
  var nonNegativeFinite = (value) => {
    const number = finiteNumber(value);
    return number !== null && number >= 0;
  };
  var phoneLinkProbeReport = (value) => {
    const status = read4(value, "status");
    const sampleCount = finiteNumber(read4(value, "sampleCount"));
    if (status === "measured" && sampleCount === 10) {
      const currentRttMs = read4(value, "currentRttMs");
      const medianRttMs = read4(value, "medianRttMs");
      const maximumRttMs = read4(value, "maximumRttMs");
      const jitterMs = read4(value, "jitterMs");
      if (nonNegativeFinite(currentRttMs) && nonNegativeFinite(medianRttMs) && nonNegativeFinite(maximumRttMs) && nonNegativeFinite(jitterMs) && maximumRttMs >= currentRttMs && maximumRttMs >= medianRttMs) {
        return Object.freeze({ status, sampleCount, currentRttMs, medianRttMs, maximumRttMs, jitterMs });
      }
    }
    if ((status === "unavailable" || status === "timed-out" || status === "disconnected") && sampleCount !== null && Number.isInteger(sampleCount) && sampleCount >= 0 && sampleCount <= 10) {
      return Object.freeze({ status, sampleCount });
    }
    return Object.freeze({ status: "unavailable", sampleCount: 0 });
  };
  var connectionEpochOf = (device) => {
    const value = read4(device, "connectionEpoch");
    return typeof value === "number" && Number.isSafeInteger(value) && value >= 0 ? value : -1;
  };
  var probeForConnectionEpoch = (deviceId, connectionEpoch) => {
    if (deviceId === null || connectionEpoch < 0) return void 0;
    const cached = phoneLinkProbes.get(deviceId);
    return cached?.connectionEpoch === connectionEpoch ? cached.report : void 0;
  };
  var milliseconds = (value) => `${Math.round(value * 10) / 10}`;
  var phoneLinkProbeLabel = (report, measuring) => {
    if (measuring) return "\u6D4B\u91CF\u4E2D\uFF1A\u6B63\u5728\u53D1\u9001 10 \u6B21 WebSocket PING";
    if (report === void 0) return "\u5C1A\u672A\u6D4B\u91CF";
    if (report.status === "measured") return `\u5F53\u524D ${milliseconds(report.currentRttMs)} ms \xB7 \u4E2D\u4F4D ${milliseconds(report.medianRttMs)} ms \xB7 \u6700\u5927 ${milliseconds(report.maximumRttMs)} ms \xB7 \u6296\u52A8 ${milliseconds(report.jitterMs)} ms`;
    if (report.status === "timed-out") return `\u4E0D\u53EF\u6D4B\uFF1A\u7B2C ${report.sampleCount + 1} \u6B21 WebSocket PING \u5728 1 \u79D2\u5185\u672A\u6536\u5230 PONG`;
    if (report.status === "disconnected") return "\u4E0D\u53EF\u6D4B\uFF1A\u624B\u673A\u8FDE\u63A5\u5DF2\u65AD\u5F00";
    return "\u4E0D\u53EF\u6D4B\uFF1A\u672A\u83B7\u5F97\u6709\u6548 WebSocket PING/PONG \u56DE\u5E94";
  };
  var optionalText = (value) => typeof value === "string" && value.trim().length > 0 ? value : null;
  var durationLabel = (value) => {
    const seconds = finiteNumber(value);
    if (seconds === null || !Number.isInteger(seconds) || seconds <= 0) return null;
    return `${Math.floor(seconds / 60)} \u5206 ${seconds % 60} \u79D2`;
  };
  var lowBatteryRthLabel = (value) => {
    if (value === "IDLE") return "\u672A\u89E6\u53D1";
    if (value === "COUNTING_DOWN") return "\u6B63\u5728\u5012\u8BA1\u65F6";
    if (value === "EXECUTED") return "\u5DF2\u6267\u884C";
    if (value === "CANCELLED") return "\u5DF2\u53D6\u6D88";
    if (value === "UNKNOWN") return "\u672A\u77E5\uFF08MSDK \u8FD4\u56DE UNKNOWN\uFF09";
    return null;
  };
  var msdkEnumLabel = (value) => {
    const raw = optionalText(value);
    if (raw === null) return null;
    return raw === "UNKNOWN" ? "\u672A\u77E5\uFF08MSDK \u8FD4\u56DE UNKNOWN\uFF09" : raw;
  };
  var msdkBooleanLabel = (value) => {
    if (value === true) return "\u662F\uFF08MSDK \u8FD4\u56DE true\uFF09";
    if (value === false) return "\u5426\uFF08MSDK \u8FD4\u56DE false\uFF09";
    return null;
  };
  var flightFactsUnconfirmed = (connection) => read4(connection, "flightController") === "unknown";
  var telemetryTimeKnown = (connection) => finiteNumber(read4(connection, "telemetryReceivedAtMs")) !== null;
  var telemetryTimeLabel = (connection) => {
    const receivedAtMs = finiteNumber(read4(connection, "telemetryReceivedAtMs"));
    if (receivedAtMs === null) return flightFactsUnconfirmed(connection) ? "\u5C1A\u672A\u6536\u5230\u6709\u6548\u72B6\u6001\uFF0C\u98DE\u63A7\u72B6\u6001\u5F53\u524D\u672A\u786E\u8BA4" : "\u5C1A\u672A\u6536\u5230\u6709\u6548\u72B6\u6001";
    const ageSeconds = Math.max(0, Math.floor((Date.now() - receivedAtMs) / 1e3));
    const clock = new Date(receivedAtMs).toLocaleTimeString("zh-CN", { hour12: false });
    if (flightFactsUnconfirmed(connection)) return `\u4E0A\u6B21\u66F4\u65B0\u4E8E ${clock}\uFF08${ageSeconds <= 2 ? "\u521A\u521A" : `${ageSeconds} \u79D2\u524D`}\uFF09\uFF0C\u98DE\u63A7\u72B6\u6001\u5F53\u524D\u672A\u786E\u8BA4`;
    return ageSeconds <= 2 ? `\u521A\u521A\uFF08${clock}\uFF09` : `${ageSeconds} \u79D2\u524D\uFF08${clock}\uFF09`;
  };
  var flightControllerStatusRows = (connection) => {
    const pose = read4(connection, "pose");
    const rthStateValue = read4(connection, "lowBatteryRthState");
    const rthState = lowBatteryRthLabel(rthStateValue);
    const remaining = rthStateValue === "UNKNOWN" || rthState === null ? null : durationLabel(read4(connection, "remainingFlightTimeSeconds"));
    const flightMode = msdkEnumLabel(read4(connection, "flightMode"));
    const altitude = finiteNumber(read4(pose, "altitudeMeters"));
    const latitude = finiteNumber(read4(pose, "latitude"));
    const longitude = finiteNumber(read4(pose, "longitude"));
    const flightState = read4(connection, "flightState") === "grounded" ? "\u5730\u9762" : read4(connection, "flightState") === "flying" ? "\u98DE\u884C\u4E2D" : "\u5C1A\u672A\u786E\u8BA4";
    const motors = read4(connection, "motorsOn") === true ? "\u5DF2\u542F\u52A8" : read4(connection, "motorsOn") === false ? "\u5DF2\u5173\u95ED" : "\u5C1A\u672A\u786E\u8BA4";
    const gpsSignal = msdkEnumLabel(read4(connection, "gpsSignalLevel"));
    const satelliteCount = finiteNumber(read4(connection, "gpsSatelliteCount"));
    const visionSensor = msdkBooleanLabel(read4(connection, "visionSensorUsed"));
    const landingConfirmation = msdkBooleanLabel(read4(connection, "landingConfirmationNeeded"));
    const takeoffFailure = msdkEnumLabel(read4(connection, "takeoffFailureError"));
    const motorStartFailure = msdkEnumLabel(read4(connection, "motorStartFailureError"));
    return [
      statusRow("\u98DE\u63A7\u8FDE\u63A5 [FlightControllerKey.KeyConnection]", connectionLabel(connection, "flightController", "\u98DE\u63A7\u5DF2\u8FDE\u63A5", "\u98DE\u63A7\u672A\u8FDE\u63A5", "\u98DE\u63A7\u72B6\u6001\u672A\u77E5"), connected(connection, "flightController")),
      statusRow("\u98DE\u884C\u72B6\u6001 [FlightControllerKey.KeyIsFlying]", flightState, false),
      statusRow("\u7535\u673A [FlightControllerKey.KeyAreMotorsOn]", motors, false),
      statusRow("\u4F4E\u7535\u91CF\u8FD4\u822A\u72B6\u6001 [FlightControllerKey.KeyLowBatteryRTHInfo]", rthState ?? "\u5C1A\u672A\u53D6\u5F97", rthState !== null),
      statusRow("\u4F4E\u7535\u91CF\u8FD4\u822A\u9884\u4F30 [FlightControllerKey.KeyLowBatteryRTHInfo]", rthStateValue === "UNKNOWN" ? "\u4E0D\u9002\u7528\uFF08\u65E0\u6709\u6548\u8FD4\u822A\u9884\u4F30\uFF09" : remaining ?? "\u5C1A\u672A\u53D6\u5F97", remaining !== null),
      statusRow("\u98DE\u884C\u6A21\u5F0F [FlightControllerKey.KeyFCFlightMode]", flightMode ?? "\u5C1A\u672A\u53D6\u5F97", flightMode !== null),
      statusRow("GPS \u4FE1\u53F7 [FlightControllerKey.KeyGPSSignalLevel]", gpsSignal ?? "\u5C1A\u672A\u53D6\u5F97", gpsSignal !== null),
      statusRow("GPS \u536B\u661F\u6570 [FlightControllerKey.KeyGPSSatelliteCount]", satelliteCount === null ? "\u5C1A\u672A\u53D6\u5F97" : String(satelliteCount), satelliteCount !== null),
      statusRow("\u89C6\u89C9\u4F20\u611F\u5668 [FlightControllerKey.KeyIsVisionSensorUsed]", visionSensor ?? "\u5C1A\u672A\u53D6\u5F97", visionSensor !== null),
      statusRow("\u964D\u843D\u786E\u8BA4 [FlightControllerKey.KeyIsLandingConfirmationNeeded]", landingConfirmation ?? "\u5C1A\u672A\u53D6\u5F97", landingConfirmation !== null),
      statusRow("\u8D77\u98DE\u5931\u8D25\u539F\u56E0 [FlightControllerKey.KeyTakeoffFailureError]", takeoffFailure ?? "\u5C1A\u672A\u53D6\u5F97", takeoffFailure !== null),
      statusRow("\u7535\u673A\u542F\u52A8\u5931\u8D25\u539F\u56E0 [FlightControllerKey.KeyMotorStartFailureError]", motorStartFailure ?? "\u5C1A\u672A\u53D6\u5F97", motorStartFailure !== null),
      statusRow("\u76F8\u5BF9\u8D77\u98DE\u70B9\u9AD8\u5EA6 [FlightControllerKey.KeyAltitude]", altitude === null ? "\u5C1A\u672A\u53D6\u5F97" : `${altitude.toFixed(1)} \u7C73`, altitude !== null),
      statusRow("\u4F4D\u7F6E [FlightControllerKey.KeyAircraftLocation]", latitude === null || longitude === null ? "\u5C1A\u672A\u53D6\u5F97" : `${latitude.toFixed(5)}, ${longitude.toFixed(5)}`, latitude !== null && longitude !== null)
    ].join("");
  };
  var flightAssistantStatusRows = (connection) => {
    const visionSystemWarning = msdkEnumLabel(read4(connection, "visionSystemWarning"));
    const visionPositioning = msdkBooleanLabel(read4(connection, "visionPositioningEnabled"));
    const landingProtection = msdkEnumLabel(read4(connection, "landingProtectionState"));
    return [
      statusRow("\u89C6\u89C9\u7CFB\u7EDF\u8B66\u544A [FlightAssistantKey.KeyVisionSystemWarning]", visionSystemWarning ?? "\u5C1A\u672A\u53D6\u5F97", visionSystemWarning !== null),
      statusRow("\u89C6\u89C9\u5B9A\u4F4D [FlightAssistantKey.KeyVisionPositioningEnabled]", visionPositioning ?? "\u5C1A\u672A\u53D6\u5F97", visionPositioning !== null),
      statusRow("\u964D\u843D\u4FDD\u62A4\u72B6\u6001 [FlightAssistantKey.KeyLandingProtectionState]", landingProtection ?? "\u5C1A\u672A\u53D6\u5F97", landingProtection !== null)
    ].join("");
  };
  var videoTransportStatusRows = (connection) => {
    const live = read4(connection, "live");
    const resolution = optionalText(read4(live, "resolution"));
    const fps = finiteNumber(read4(live, "fps"));
    const bitrate = finiteNumber(read4(live, "videoBitrateKbps"));
    const rtt = finiteNumber(read4(live, "rttMillis"));
    const packetLoss = finiteNumber(read4(live, "packetLoss"));
    const packetCacheLength = finiteNumber(read4(live, "packetCacheLength"));
    const streaming = read4(live, "streaming") === true ? "MSDK \u62A5\u544A\u6B63\u5728\u63A8\u6D41" : read4(live, "streaming") === false ? "MSDK \u62A5\u544A\u672A\u63A8\u6D41" : "\u5C1A\u672A\u53D6\u5F97";
    return [
      statusRow("AirLink \u8FDE\u63A5 [AirLinkKey.KeyConnection]", connectionLabel(connection, "airLink", "AirLink \u5DF2\u8FDE\u63A5", "AirLink \u672A\u8FDE\u63A5", "AirLink \u72B6\u6001\u672A\u77E5"), connected(connection, "airLink")),
      statusRow("\u4E3B\u76F8\u673A\u8FDE\u63A5 [CameraKey.KeyConnection, LEFT_OR_MAIN]", connectionLabel(connection, "camera", "\u4E3B\u76F8\u673A\u5DF2\u8FDE\u63A5", "\u4E3B\u76F8\u673A\u672A\u8FDE\u63A5", "\u4E3B\u76F8\u673A\u72B6\u6001\u672A\u77E5"), connected(connection, "camera")),
      statusRow("MSDK \u56FE\u4F20\u89C2\u6D4B [\u624B\u673A MSDK \u56FE\u4F20\u8FD0\u884C\u89C2\u6D4B]", streaming, read4(live, "streaming") === true),
      statusRow("\u56FE\u4F20\u5206\u8FA8\u7387 [\u624B\u673A MSDK \u56FE\u4F20\u8FD0\u884C\u89C2\u6D4B]", resolution ?? "\u5C1A\u672A\u53D6\u5F97", resolution !== null),
      statusRow("\u56FE\u4F20\u5E27\u7387 [\u624B\u673A MSDK \u56FE\u4F20\u8FD0\u884C\u89C2\u6D4B]", fps === null ? "\u5C1A\u672A\u53D6\u5F97" : `${fps} fps`, fps !== null),
      statusRow("\u56FE\u4F20\u7801\u7387 [\u624B\u673A MSDK \u56FE\u4F20\u8FD0\u884C\u89C2\u6D4B]", bitrate === null ? "\u5C1A\u672A\u53D6\u5F97" : `${bitrate} Kbps`, bitrate !== null),
      statusRow("\u56FE\u4F20\u4E22\u5305 [LiveStreamStatus.packetLoss]", packetLoss === null ? "\u5C1A\u672A\u53D6\u5F97" : String(packetLoss), packetLoss !== null),
      statusRow("\u56FE\u4F20\u7F13\u5B58\u957F\u5EA6 [LiveStreamStatus.packetCacheLen]", packetCacheLength === null ? "\u5C1A\u672A\u53D6\u5F97" : String(packetCacheLength), packetCacheLength !== null),
      statusRow("MSDK \u8FD0\u884C\u671F\u9519\u8BEF [LiveStreamStatusListener.onError]", liveRuntimeErrorStatus(connection), false),
      statusRow("\u56FE\u4F20\u5F80\u8FD4\u65F6\u95F4 [\u624B\u673A MSDK \u56FE\u4F20\u8FD0\u884C\u89C2\u6D4B]", rtt === null ? "\u5C1A\u672A\u53D6\u5F97" : `${rtt} ms`, rtt !== null)
    ].join("") + cameraFrameStatusRows(connection);
  };
  var batteryStatusRows = (connection) => {
    const battery = finiteNumber(read4(connection, "batteryPercent"));
    const batteryConnection = connectionLabel(connection, "battery", "\u5DF2\u8FDE\u63A5", "\u5DF2\u65AD\u5F00", "\u72B6\u6001\u672A\u77E5");
    return [
      statusRow("\u4E3B\u7535\u6C60\u8FDE\u63A5 [BatteryKey.KeyConnection, LEFT_OR_MAIN]", batteryConnection, connected(connection, "battery")),
      statusRow("\u7535\u91CF [BatteryKey.KeyChargeRemainingInPercent, LEFT_OR_MAIN]", battery === null ? "\u5C1A\u672A\u53D6\u5F97" : `${battery}%`, battery !== null)
    ].join("");
  };
  var deviceInformationRows = (connection) => {
    const aircraftModel = optionalText(read4(connection, "aircraftModel"));
    const remoteControllerModel = optionalText(read4(connection, "remoteControllerModel"));
    return [
      statusRow("\u673A\u578B [ProductKey.KeyProductType]", aircraftModel ?? "\u5C1A\u672A\u53D6\u5F97", aircraftModel !== null),
      statusRow("\u9065\u63A7\u5668\u578B\u53F7 [RemoteControllerKey.KeyRemoteControllerType]", remoteControllerModel ?? "\u5C1A\u672A\u53D6\u5F97", remoteControllerModel !== null),
      statusRow("\u72B6\u6001\u66F4\u65B0\u65F6\u95F4 [\u684C\u9762\u63A5\u6536\u65F6\u95F4]", telemetryTimeLabel(connection), telemetryTimeKnown(connection) && !flightFactsUnconfirmed(connection))
    ].join("");
  };
  var missionRuntimeLabel = (mission) => {
    switch (read4(mission, "phase")) {
      case "staging":
        return "\u51C6\u5907\u4E2D";
      case "staged":
        return "\u5DF2\u6682\u5B58\u5230\u624B\u673A";
      case "uploading":
        return "\u4E0A\u4F20\u4E2D";
      case "uploaded":
        return "\u5DF2\u4E0A\u4F20\u81F3\u98DE\u673A";
      case "starting":
        return "\u542F\u52A8\u4E2D\uFF0C\u7B49\u5F85\u98DE\u673A\u786E\u8BA4";
      case "running":
        return "\u6267\u884C\u4E2D";
      case "pausing":
        return "\u6682\u505C\u4E2D";
      case "paused":
        return "\u5DF2\u6682\u505C";
      case "resuming":
        return "\u6062\u590D\u4E2D";
      case "stopping":
        return "\u505C\u6B62\u4E2D";
      case "completed":
        return "\u5DF2\u5B8C\u6210";
      case "failed":
        return "\u5931\u8D25";
      case "disconnected":
        return "\u94FE\u8DEF\u4E2D\u65AD";
      case "idle":
        return "\u672A\u5F00\u59CB";
      default:
        return "\u72B6\u6001\u672A\u77E5";
    }
  };
  var streamRuntimeLabel = (device) => {
    const stream = read4(device, "stream");
    const live = read4(read4(device, "connection"), "live");
    switch (read4(stream, "phase")) {
      case "starting":
        return "\u542F\u52A8\u4E2D";
      case "stopping":
        return "\u505C\u6B62\u4E2D";
      case "failed":
        return "\u5931\u8D25";
      case "disconnected":
        return "\u94FE\u8DEF\u4E2D\u65AD";
      case "streaming":
        return read4(live, "streaming") === true ? "\u63A8\u6D41\u4E2D" : read4(live, "streaming") === false ? "\u5DF2\u63A5\u6536\u542F\u52A8\u547D\u4EE4\uFF0C\u7B49\u5F85\u63A8\u6D41" : "\u5DF2\u63A5\u6536\u542F\u52A8\u547D\u4EE4\uFF0C\u7B49\u5F85\u72B6\u6001\u786E\u8BA4";
      case "idle":
        return read4(live, "streaming") === true ? "MSDK \u62A5\u544A\u63A8\u6D41\u4E2D\uFF08\u975E\u5F53\u524D\u63A7\u5236\u4F1A\u8BDD\uFF09" : "\u5DF2\u505C\u6B62";
      default:
        return "\u72B6\u6001\u672A\u77E5";
    }
  };
  var playbackRuntimeLabel = (device, streamDeviceId) => {
    if (read4(device, "deviceId") !== streamDeviceId) return "\u672A\u9009\u4E3A\u5F53\u524D\u64AD\u653E\u5668";
    const video = read4(device, "video");
    switch (read4(video, "phase")) {
      case "awaiting-ingest":
        return "\u7B49\u5F85\u6D41\u8FDB\u5165";
      case "awaiting-playback":
        return "\u7B49\u5F85\u64AD\u653E\u5668";
      case "failed":
        return "\u64AD\u653E\u5931\u8D25";
      case "ready": {
        if (read4(video, "selected") !== true) return "\u7B49\u5F85\u64AD\u653E\u5668";
        const player = document.getElementById("video");
        if (!(player instanceof HTMLVideoElement)) return "\u64AD\u653E\u5668\u672A\u5C31\u7EEA";
        if (player.error !== null) return "\u64AD\u653E\u5668\u62A5\u544A\u9519\u8BEF";
        if (isPainting(player)) return "\u6B63\u5728\u89E3\u7801\u5E76\u51FA\u753B";
        if (player.buffered.length > 0 || player.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA) {
          return "\u5DF2\u6536\u5230\u5A92\u4F53\u6570\u636E\uFF0C\u7B49\u5F85\u89E3\u7801\u6216\u51FA\u753B";
        }
        return "\u7B49\u5F85\u5A92\u4F53\u6570\u636E";
      }
      case "unavailable":
        return "\u672A\u5F00\u59CB";
      default:
        return "\u72B6\u6001\u672A\u77E5";
    }
  };
  var liveRuntimeErrorStatus = (connection) => {
    const runtimeError = read4(read4(connection, "live"), "runtimeError");
    const code = optionalText(read4(runtimeError, "code"));
    const description = optionalText(read4(runtimeError, "description"));
    return code !== null && description !== null ? `\u9519\u8BEF\u7801\uFF1A${code}\uFF1B\u9519\u8BEF\u8BF4\u660E\uFF1A${description}` : "\u672A\u6536\u5230\u8FD0\u884C\u671F\u9519\u8BEF";
  };
  var desktopMediaServiceStatus = (view, service) => {
    switch (read4(read4(view, "media"), service)) {
      case "listening":
        return "\u6B63\u5728\u76D1\u542C";
      case "idle":
        return "\u672A\u542F\u52A8";
      case "failed":
        return "\u670D\u52A1\u5931\u8D25";
      default:
        return "\u5C1A\u672A\u53D6\u5F97";
    }
  };
  var rtmpArrivalStatus = (device) => {
    if (device === void 0) return "\u672A\u9009\u62E9\u624B\u673A";
    switch (read4(read4(device, "video"), "phase")) {
      case "ready":
        return "\u5DF2\u89C2\u6D4B\u5230\u5F53\u524D\u8BBE\u5907 RTMP \u6D41";
      case "awaiting-ingest":
        return "\u5C1A\u672A\u89C2\u6D4B\u5230\u5F53\u524D\u8BBE\u5907 RTMP \u6D41";
      case "awaiting-playback":
        return "\u5DF2\u63A5\u6536\u6D41\uFF0C\u7B49\u5F85\u64AD\u653E\u5668";
      case "failed":
        return "\u6D41\u63A5\u6536\u6216\u5065\u5EB7\u68C0\u67E5\u5931\u8D25";
      default:
        return "\u5F53\u524D\u8BBE\u5907\u5C1A\u65E0\u5A92\u4F53\u6D41";
    }
  };
  var desktopPlayerSourceStatus = (device) => {
    if (device === void 0) return "\u672A\u9009\u62E9\u624B\u673A";
    switch (read4(read4(device, "video"), "playerPhase")) {
      case "playing":
        return "\u5DF2\u9009\u62E9\u5F53\u524D HTTP-FLV \u6570\u636E\u6E90";
      case "failed":
        return "\u64AD\u653E\u5668\u6570\u636E\u6E90\u5931\u8D25";
      case "idle":
        return "\u5F53\u524D\u8BBE\u5907\u672A\u88AB\u64AD\u653E\u5668\u9009\u62E9";
      default:
        return "\u5C1A\u672A\u53D6\u5F97";
    }
  };
  var cameraFrameStatusRows = (connection) => [
    statusRow("\u76F8\u673A\u7F16\u7801\u5E27\u72B6\u6001 [ICameraStreamManager.ReceiveStreamListener]", cameraFrameStatus(connection), read4(cameraFrameFact(connection), "state") === "receiving"),
    statusRow("\u5E27\u89C2\u5BDF\u4EE3\u6B21 [\u751F\u4EA7 RTMP \u4F1A\u8BDD\u4EE3\u6B21]", cameraFrameGenerationStatus(connection), false),
    statusRow("\u6709\u6548\u7F16\u7801\u5E27\u6570\u91CF [ReceiveStreamListener]", cameraFrameCountStatus(connection), read4(cameraFrameFact(connection), "receivedFrameCount") !== 0),
    statusRow("\u6700\u65B0\u5E27\u5E74\u9F84 [\u624B\u673A\u672C\u5730\u5355\u8C03\u65F6\u949F]", cameraFrameAgeStatus(connection), false),
    statusRow("\u6700\u65B0\u5E27\u683C\u5F0F [DJI StreamInfo]", cameraFrameFormatStatus(connection), false)
  ].join("");
  var desktopMediaStatusRows = (view, device, streamDeviceId) => [
    statusRow("RTMP \u63A5\u6536\u670D\u52A1 [\u684C\u9762 media-pipeline]", desktopMediaServiceStatus(view, "rtmpIngest"), desktopMediaServiceStatus(view, "rtmpIngest") === "\u6B63\u5728\u76D1\u542C"),
    statusRow("HTTP-FLV \u670D\u52A1 [\u684C\u9762 media-pipeline]", desktopMediaServiceStatus(view, "httpFlv"), desktopMediaServiceStatus(view, "httpFlv") === "\u6B63\u5728\u76D1\u542C"),
    statusRow("\u5F53\u524D\u8BBE\u5907 RTMP \u63A5\u6536 [\u684C\u9762 rtmp-ingest]", rtmpArrivalStatus(device), read4(read4(device, "video"), "phase") === "ready"),
    statusRow("\u64AD\u653E\u5668\u6570\u636E\u6E90 [\u684C\u9762 video-player]", desktopPlayerSourceStatus(device), read4(read4(device, "video"), "playerPhase") === "playing"),
    statusRow("\u5B9E\u9645\u6E32\u67D3 [HTMLVideoElement]", playbackRuntimeLabel(device, streamDeviceId), false)
  ].join("");
  var runtimeStatusRows = (view, device, streamDeviceId) => [
    statusRow("\u4EFB\u52A1 [\u624B\u673A\u4EFB\u52A1\u8FD0\u884C\u72B6\u6001]", missionRuntimeLabel(read4(device, "mission")), false),
    statusRow("\u5F53\u524D\u822A\u70B9 [WaylineExecutingInfo.getCurrentWaypointIndex]", missionWaypointIndexStatus(device), false),
    statusRow("\u822A\u7EBF ID [WaylineExecutingInfo.getWaylineID]", missionWaylineIdStatus(device), false),
    statusRow("\u98DE\u673A\u6B63\u5728\u6267\u884C\u7684\u6587\u4EF6 [WaylineExecutingInfo.getMissionFileName]", missionExecutingFileStatus(device), false),
    statusRow("\u822A\u70B9\u52A8\u4F5C [WaypointActionListener]", missionWaypointActionStatus(device), false),
    statusRow("\u822A\u7EBF\u4E2D\u65AD\u539F\u56E0 [WaylineExecutingInfoListener]", missionWaylineInterruptStatus(device), false),
    statusRow("\u624B\u673A\u63A8\u6D41 [\u624B\u673A\u56FE\u4F20\u8FD0\u884C\u72B6\u6001]", streamRuntimeLabel(device), false)
  ].join("") + desktopMediaStatusRows(view, device, streamDeviceId);
  async function projectView(signal) {
    const snapshotResult = unwrap2(await awaitCurrentRender(bridge().invoke("state-snapshot"), signal));
    lastSnapshot = unwrap2(snapshotResult) ?? lastSnapshot;
    try {
      const hintResult = unwrap2(await awaitCurrentRender(bridge().invoke("network-hint"), signal));
      const listed = read4(hintResult, "hints");
      const liveHints = Array.isArray(listed) ? listed.filter((item) => typeof item === "string" && item.startsWith("ws://")) : [];
      if (liveHints.length > 0) lastRelayHint = liveHints.join(" \u6216 ");
    } catch {
    }
    const view = projectCached();
    state.missionDeviceId = view.missionDeviceId;
    state.streamDeviceId = view.streamDeviceId;
    return view;
  }
  var projectCached = () => {
    const retainedPhotoTarget = photoProgressCache.get(photoProgressDeviceId) === void 0 ? null : photoProgressDeviceId;
    const view = OperatorConsole.project({
      snapshot: lastSnapshot,
      selection: { missionDeviceId: state.missionDeviceId, streamDeviceId: state.streamDeviceId ?? retainedPhotoTarget },
      workspace: state.workspace === "monitor" ? "flight" : state.workspace,
      relayHint: lastRelayHint.length > 0 ? lastRelayHint : bridge().relayHint
    });
    photoProgressCache.observe(view.devices, Date.now());
    if (view.streamDeviceId !== null) photoProgressDeviceId = view.streamDeviceId;
    return view;
  };
  var photoDisplayDeviceId = (view) => view.streamDeviceId ?? (photoProgressCache.get(photoProgressDeviceId) === void 0 ? null : photoProgressDeviceId);
  var blocked = (action, reason, deviceId = null, connectionEpoch = null) => {
    const feedback = captureFeedback(action, deviceId, connectionEpoch, { ok: false, code: "DESKTOP_BLOCKED", reason });
    show(feedback.message);
    void bridge().invoke("diagnostics-record", { action, reason });
  };
  async function run(action, invokeName, input, knownView, feedbackAction = action) {
    const view = knownView ?? projectCached();
    const decision = OperatorConsole.evaluate(action, view);
    const deviceId = text4(read4(input, "deviceId"));
    const connectionEpoch = feedbackDeviceEpoch(view, deviceId);
    if (!decision.ok) {
      blocked(action, decision.reason ?? "\u65E0\u6CD5\u6267\u884C", deviceId, connectionEpoch);
      return;
    }
    if (action === "photo-capture") show("\u6B63\u5728\u62CD\u7167");
    if (action === "photo-fetch") show("\u6B63\u5728\u8BF7\u6C42\u56DE\u4F20\u7535\u8111\u4E0A\u6CA1\u6709\u7684\u7167\u7247");
    let result;
    try {
      result = await bridge().invoke(invokeName, input);
    } catch {
      result = { ok: false, code: "DEPENDENCY_FAILURE" };
    }
    if (invokeName === "flight-request") {
      const requestedAction = text4(read4(input, "action")) ?? "takeoff";
      const confirmation = confirmationFromResult(result, deviceId, requestedAction);
      if (confirmation !== null) pendingFlightConfirmation = confirmation;
    } else if ((action === "flight-confirm" || action === "flight-cancel") && accepted(unwrapAll(result))) {
      pendingFlightConfirmation = null;
    }
    const feedback = captureFeedback(feedbackAction, deviceId, connectionEpoch, result);
    show(feedback.message);
    try {
      await render();
    } catch {
    }
  }
  function renderDevices(view) {
    const devices = view.devices;
    const count = el("connection-count");
    count.textContent = devices.length > 0 ? `${devices.length} \u53F0\u624B\u673A\u5DF2\u8FDE\u63A5` : "\u7B49\u5F85\u624B\u673A\u8FDE\u63A5";
    count.classList.toggle("online", devices.length > 0);
    el("device-list").replaceChildren(...devices.map((device) => {
      const node = document.createElement("div");
      node.className = "device";
      if (device.deviceId === view.missionDeviceId) node.classList.add("inspected");
      const connection2 = device.connection ?? {};
      node.innerHTML = `<strong>${escapeHtml(callSign(String(device.deviceId)))}</strong><div class="muted">${connectionLabel(connection2, "remoteController", "\u9065\u63A7\u5668\u5DF2\u8FDE\u63A5", "\u9065\u63A7\u5668\u672A\u8FDE\u63A5", "\u9065\u63A7\u5668\u72B6\u6001\u672A\u77E5")} \xB7 ${connectionLabel(connection2, "flightController", "\u98DE\u63A7\u5DF2\u8FDE\u63A5", "\u98DE\u63A7\u672A\u8FDE\u63A5", "\u98DE\u63A7\u72B6\u6001\u672A\u77E5")}</div>`;
      node.addEventListener("click", () => {
        state.missionDeviceId = String(device.deviceId);
        void render();
      });
      return node;
    }));
    const inspected = devices.find((item) => item.deviceId === view.missionDeviceId);
    const refresh = el("device-refresh");
    refresh.disabled = inspected === void 0;
    refresh.title = inspected === void 0 ? "\u8BF7\u5148\u9009\u62E9\u5DF2\u8FDE\u63A5\u7684\u624B\u673A" : "\u8BFB\u53D6\u5F53\u524D\u624B\u673A\u72B6\u6001";
    const measure = el("device-link-measure");
    const inspectedDeviceId = inspected === void 0 ? null : String(inspected.deviceId);
    const inspectedEpoch = connectionEpochOf(inspected);
    const phoneLinkProbe = probeForConnectionEpoch(inspectedDeviceId, inspectedEpoch);
    const measuring = inspectedDeviceId !== null && phoneLinkProbeInFlightDeviceId === inspectedDeviceId;
    measure.disabled = inspectedDeviceId === null || measuring;
    measure.title = inspectedDeviceId === null ? "\u8BF7\u5148\u9009\u62E9\u5DF2\u8FDE\u63A5\u7684\u624B\u673A" : measuring ? "\u6B63\u5728\u6D4B\u91CF\u624B\u673A\u8FDE\u63A5" : "\u4EC5\u6D4B\u91CF\u7535\u8111\u4E0E\u624B\u673A\u7684 WebSocket \u5F80\u8FD4\u65F6\u95F4";
    const pairingOperation = pairingControl(view);
    const pendingPairing = pairingCommandInFlight;
    const pendingForSelection = pendingPairing !== null && pendingPairing.deviceId === inspectedDeviceId;
    const visiblePairingAction = pendingForSelection ? pendingPairing.action : pairingOperation.action;
    for (const action of ["pairing-start", "pairing-stop"]) {
      const button = el(action);
      const decision = OperatorConsole.evaluate(action, view);
      button.hidden = action !== visiblePairingAction;
      const waiting = pendingForSelection || pairingOperation.stopping && action === "pairing-stop";
      button.textContent = waiting ? action === "pairing-start" ? "\u6B63\u5728\u5F00\u59CB\u2026" : "\u6B63\u5728\u505C\u6B62\u2026" : action === "pairing-start" ? "\u5F00\u59CB\u5BF9\u9891" : "\u505C\u6B62\u5BF9\u9891";
      button.disabled = pendingPairing !== null || pairingOperation.stopping || !decision.ok;
      button.setAttribute("aria-busy", String(waiting));
      const reason = pendingPairing !== null ? pendingForSelection ? "\u6B63\u5728\u5904\u7406\u5BF9\u9891\u547D\u4EE4" : "\u6B63\u5728\u5904\u7406\u5176\u4ED6\u8BBE\u5907\u7684\u5BF9\u9891\u547D\u4EE4" : pairingOperation.stopping ? "\u6B63\u5728\u7B49\u5F85\u624B\u673A\u786E\u8BA4\u505C\u6B62\u5BF9\u9891" : decision.ok ? null : decision.reason ?? "\u5F53\u524D\u4E0D\u53EF\u64CD\u4F5C";
      button.title = reason ?? (action === "pairing-start" ? "\u8BA9\u9065\u63A7\u5668\u5F00\u59CB\u5BF9\u9891" : "\u7ED3\u675F\u5F53\u524D\u5BF9\u9891");
      if (!button.hidden) el("pairing-availability").textContent = `${button.textContent}\uFF1A${reason ?? "\u53EF\u64CD\u4F5C"}`;
    }
    renderOperationFeedback("pairing", inspectedDeviceId, feedbackDeviceEpoch(view, inspectedDeviceId));
    const connection = inspected === void 0 ? {} : inspected.connection ?? {};
    const msdk = msdkFact(connection);
    const pairing = pairingFact(connection);
    const html = inspected === void 0 ? "\u4ECE\u5DE6\u4FA7\u9009\u62E9\u5DF2\u8FDE\u63A5\u7684\u624B\u673A\u3002" : `<p class="muted">${escapeHtml(callSign(String(inspected.deviceId)))}</p>
      <h3 class="device-status-heading">\u8FDE\u63A5\u72B6\u6001</h3>
      <div class="connection-status-list" aria-label="\u8FDE\u63A5\u72B6\u6001">
        ${statusRow("\u7535\u8111\u5230\u624B\u673A\u4E2D\u7EE7 [\u684C\u9762 Relay Session]", "\u4E2D\u7EE7\u5728\u7EBF", true)}
        ${statusRow("\u624B\u673A\u8FDE\u63A5\u8D28\u91CF [WebSocket PING/PONG]", phoneLinkProbeLabel(phoneLinkProbe, measuring), !measuring && phoneLinkProbe?.status === "measured")}
        ${statusRow("MSDK \u751F\u547D\u5468\u671F [SDKManager]", msdk.label, msdk.ok)}
        ${statusRow("\u9065\u63A7\u5668\u8FDE\u63A5 [RemoteControllerKey.KeyConnection]", connectionLabel(connection, "remoteController", "\u9065\u63A7\u5668\u5DF2\u8FDE\u63A5", "\u9065\u63A7\u5668\u672A\u8FDE\u63A5", "\u9065\u63A7\u5668\u72B6\u6001\u672A\u77E5"), connected(connection, "remoteController"))}
        ${statusRow("\u5BF9\u9891\u72B6\u6001 [RemoteControllerKey.KeyPairingStatus]", pairing.label, pairing.ok)}
      </div>
      <h3 class="device-status-heading">\u98DE\u63A7\u72B6\u6001 [FlightControllerKey]</h3>
      <div class="connection-status-list" aria-label="\u98DE\u63A7\u72B6\u6001">${flightControllerStatusRows(connection)}</div>
      <h3 class="device-status-heading">\u98DE\u884C\u8F85\u52A9\u72B6\u6001 [FlightAssistantKey]</h3>
      <div class="connection-status-list" aria-label="\u98DE\u884C\u8F85\u52A9\u72B6\u6001">${flightAssistantStatusRows(connection)}</div>
      <h3 class="device-status-heading">\u56FE\u4F20\u72B6\u6001 [AirLinkKey / CameraKey]</h3>
      <div class="connection-status-list" aria-label="\u56FE\u4F20\u72B6\u6001">${videoTransportStatusRows(connection)}</div>
      <h3 class="device-status-heading">\u7535\u6C60\u72B6\u6001 [BatteryKey]</h3>
      <div class="connection-status-list" aria-label="\u7535\u6C60\u72B6\u6001">${batteryStatusRows(connection)}</div>
      <h3 class="device-status-heading">\u8BBE\u5907\u4FE1\u606F</h3>
      <div class="connection-status-list" aria-label="\u8BBE\u5907\u4FE1\u606F">${deviceInformationRows(connection)}</div>
      <h3 class="device-status-heading">\u8FD0\u884C\u72B6\u6001</h3>
      <div class="connection-status-list" aria-label="\u8FD0\u884C\u72B6\u6001">${runtimeStatusRows(view, inspected, view.streamDeviceId)}</div>
      <p class="muted">\u5BF9\u9891\u4EC5\u7528\u4E8E\u65B0\u589E\u98DE\u673A\u6216\u66F4\u6362\u9065\u63A7\u5668\u3002\u5DF2\u7ED1\u5B9A\u7684\u98DE\u673A\u5F00\u673A\u540E\u4F1A\u81EA\u52A8\u8FDE\u63A5\uFF0C\u98DE\u63A7\u5DF2\u8FDE\u63A5\u65F6\u65E0\u9700\u91CD\u65B0\u5BF9\u9891\uFF1B\u5BF9\u9891\u72B6\u6001\u4E0D\u7528\u4E8E\u5224\u65AD\u5F53\u524D\u98DE\u673A\u662F\u5426\u5DF2\u8FDE\u63A5\u3002\u547D\u4EE4\u5B8C\u6210\u540E\uFF0C\u8BF7\u4EE5\u624B\u673A\u56DE\u62A5\u7684\u72B6\u6001\u4E3A\u51C6\u3002</p>`;
    if (html !== lastDeviceDetailHtml) {
      el("device-detail").innerHTML = html;
      lastDeviceDetailHtml = html;
    }
    el("device-guide").textContent = `\u7535\u8111\u548C\u624B\u673A\u8FDE\u63A5\u540C\u4E00 Wi-Fi\uFF0C\u6253\u5F00\u624B\u673A\u4E2D\u7EE7\u5E76\u6388\u4E88\u6240\u9700\u6743\u9650\uFF0C\u624B\u673A\u4F1A\u81EA\u52A8\u53D1\u73B0\u5E76\u8FDE\u63A5\u7535\u8111\u3002\u9700\u8981\u624B\u52A8\u8FDE\u63A5\u65F6\uFF0C\u957F\u6309\u624B\u673A\u9875\u9762\u6807\u9898\u6253\u5F00\u8C03\u8BD5\u5165\u53E3\uFF0C\u586B\u5199 ${view.relayHint}\u3002\u5DF2\u5BF9\u9891\u7684\u98DE\u673A\u4F1A\u5728\u5F00\u673A\u540E\u81EA\u52A8\u8FDE\u63A5\uFF1B\u53EA\u6709\u65B0\u589E\u98DE\u673A\u6216\u66F4\u6362\u9065\u63A7\u5668\u65F6\uFF0C\u624D\u5728\u8FD9\u91CC\u5F00\u59CB\u5BF9\u9891\u3002\u81EA\u52A8\u8FDE\u63A5\u6A21\u5F0F\u4E0B\uFF0C\u7535\u8111\u6062\u590D\u540E\u624B\u673A\u4F1A\u91CD\u65B0\u53D1\u73B0\u5E76\u8FDE\u63A5\u3002`;
  }
  var routeMenuOpen = false;
  var setRouteMenuOpen = (open) => {
    routeMenuOpen = open;
    const list = el("route-select-list");
    const trigger = el("route-select");
    list.hidden = !open;
    trigger.setAttribute("aria-expanded", open ? "true" : "false");
  };
  function renderRoutes(view) {
    const selected = view.selectedRoute;
    const hasRoute = view.routes.length > 0;
    el("route-picker-wrap").hidden = !hasRoute;
    el("route-details").hidden = selected === null || selected === void 0;
    el("route-empty").hidden = hasRoute;
    if (!hasRoute) setRouteMenuOpen(false);
    el("route-select-label").textContent = selected?.displayName ?? "\u5C1A\u672A\u9009\u62E9";
    const list = el("route-select-list");
    list.replaceChildren(...view.routes.map((route) => {
      const current = selected?.routeId === route.routeId;
      const option = document.createElement("button");
      option.type = "button";
      option.className = current ? "route-menu-option is-selected" : "route-menu-option";
      option.setAttribute("role", "option");
      option.setAttribute("aria-selected", current ? "true" : "false");
      const name = document.createElement("span");
      name.textContent = route.displayName;
      const hint = document.createElement("small");
      hint.className = route.executable ? "is-ready" : "is-preview";
      hint.textContent = route.executable ? "\u53EF\u6267\u884C" : "\u4EC5\u9884\u89C8";
      option.append(name, hint);
      option.addEventListener("click", async (event) => {
        event.stopPropagation();
        setRouteMenuOpen(false);
        if (current) return;
        const decision = OperatorConsole.evaluate("select-route", view);
        if (!decision.ok) {
          show(decision.reason ?? "\u65E0\u6CD5\u9009\u62E9\u822A\u7EBF");
          return;
        }
        await bridge().invoke("route-select", { routeId: route.routeId });
        await render();
      });
      return option;
    }));
    setRouteMenuOpen(routeMenuOpen);
    if (selected === null || selected === void 0) {
      el("route-file-name").textContent = "";
      el("route-meta").textContent = "";
      el("route-executable").textContent = "";
      el("route-executable").classList.remove("is-blocked");
      el("route-summary").textContent = hasRoute ? "\u8BF7\u9009\u62E9\u8981\u9884\u89C8\u7684\u822A\u7EBF" : "\u5C1A\u672A\u5BFC\u5165\u822A\u8FF9\u6587\u4EF6";
      return;
    }
    el("route-file-name").textContent = selected.displayName;
    const executableLabel = selected.executable ? "\u53EF\u63D0\u4EA4\u7ED9\u98DE\u673A" : selected.blockedReason ?? "\u5F53\u524D\u822A\u7EBF\u4E0D\u80FD\u63D0\u4EA4\u7ED9\u98DE\u673A";
    el("route-meta").textContent = selected.executable ? "\u684C\u9762\u6587\u4EF6\u68C0\u67E5\u5DF2\u901A\u8FC7\uFF0C\u98DE\u673A\u4ECD\u53EF\u80FD\u5728\u4E0A\u4F20\u6216\u6267\u884C\u65F6\u62D2\u7EDD" : `${executableLabel} \xB7 \u8981\u98DE\u8FD9\u6761\u822A\u7EBF\u9700\u8981 Wayline \u5BFC\u51FA\u7684 KMZ`;
    el("route-executable").textContent = executableLabel;
    el("route-executable").classList.toggle("is-blocked", selected.executable !== true);
  }
  function renderActionRequests(view) {
    document.querySelectorAll("button[data-action]").forEach((button) => {
      const action = button.dataset.action ?? "";
      const deviceId = action.startsWith("stream-") || action.startsWith("photo-") ? view.streamDeviceId : view.missionDeviceId;
      const reason = actionRequests.reason(deviceId, action);
      if (reason === null) return;
      button.disabled = true;
      button.title = reason;
      if (!actionRequests.pending(deviceId, action)) return;
      if (action === "photo-fetch") {
        renderFlightPhotoProgress(view);
        return;
      }
      const note = document.querySelector(`[data-operation-feedback="${action}"]`);
      if (note instanceof HTMLElement) {
        note.hidden = false;
        note.textContent = "\u6B63\u5728\u5904\u7406\uFF0C\u8BF7\u7A0D\u5019";
        note.dataset.feedbackSource = "desktop";
        note.dataset.feedbackOutcome = "pending";
      }
    });
  }
  function renderMissionButtons(view) {
    const missionButtonActions = Object.freeze({
      stage: "mission-stage",
      upload: "mission-upload",
      start: "mission-start",
      pause: "mission-pause",
      resume: "mission-resume",
      stop: "mission-stop"
    });
    for (const [action, dataAction] of Object.entries(missionButtonActions)) {
      const button = document.querySelector(`button[data-action="${dataAction}"]`);
      if (!(button instanceof HTMLButtonElement)) continue;
      const availability = view.missionActions[action];
      button.disabled = !availability.enabled;
      button.title = availability.enabled ? button.textContent ?? "" : availability.reason ?? "\u5F53\u524D\u9636\u6BB5\u4E0D\u80FD\u6267\u884C\u6B64\u64CD\u4F5C";
      renderOperationFeedback(dataAction, view.missionDeviceId, feedbackDeviceEpoch(view, view.missionDeviceId));
    }
  }
  function renderFlightControllerVideoHint(view) {
    const paint = (id, text5) => {
      const node = el(id);
      if (text5 === null) {
        node.hidden = true;
        node.textContent = "";
        return;
      }
      node.hidden = false;
      node.textContent = text5;
    };
    paint("flight-fc-banner", view.flightControllerVideoBanner);
    paint("flight-fc-repeat", view.flightControllerVideoAfterStart);
  }
  function renderStreamButtons(view) {
    const streamStopping = view.streamLabel === "\u6B63\u5728\u505C\u6B62\u56FE\u4F20";
    const streamHasDjiRuntimeError = view.streamLabel.startsWith("DJI MSDK \u56FE\u4F20\u8FD0\u884C\u56DE\u8C03\uFF1A");
    el("stream-label").textContent = view.streamLabel;
    el("stream-label").classList.toggle("ok", !streamStopping && !streamHasDjiRuntimeError && (view.playbackReady || view.streamCanStart));
    const startButton = document.querySelector('button[data-action="stream-start"]');
    if (startButton instanceof HTMLButtonElement) {
      startButton.disabled = !view.streamCanStart;
      startButton.textContent = streamStopping && view.streamCanStart ? "\u505C\u6B62\u540E\u91CD\u542F\u56FE\u4F20" : "\u542F\u52A8\u56FE\u4F20";
      startButton.title = view.streamCanStart ? streamStopping ? "\u624B\u673A\u786E\u8BA4\u505C\u6B62\u540E\u81EA\u52A8\u91CD\u65B0\u542F\u52A8\u56FE\u4F20" : "\u56FE\u4F20\u53EF\u8BF7\u6C42\u542F\u52A8\uFF1A\u624B\u673A\u4E2D\u7EE7\u3001MSDK\u3001AirLink \u548C\u4E3B\u76F8\u673A\u5747\u5DF2\u5C31\u7EEA\uFF1B\u53D1\u9001\u524D\u4F1A\u68C0\u67E5\u7535\u8111\u63A5\u6536\u7AEF\uFF0C\u5B9E\u9645\u63A8\u6D41\u548C\u51FA\u753B\u4ECD\u5206\u522B\u786E\u8BA4" : view.streamLabel;
    }
    const stopButton = document.querySelector('button[data-action="stream-stop"]');
    if (stopButton instanceof HTMLButtonElement) {
      const canStop = !view.streamSourceUnavailable && !streamStopping && (view.streamCanStop || attachedUrl !== null);
      stopButton.disabled = !canStop;
      stopButton.title = canStop ? "\u505C\u6B62\u56FE\u4F20" : view.streamSourceUnavailable ? "\u56FE\u4F20\u6E90\u5DF2\u65AD\u5F00\uFF0C\u624B\u673A\u5DF2\u81EA\u52A8\u505C\u6B62\u56FE\u4F20" : streamStopping ? "\u6B63\u5728\u7B49\u5F85\u624B\u673A\u786E\u8BA4\u505C\u6B62" : "\u5F53\u524D\u6CA1\u6709\u8FDB\u884C\u4E2D\u7684\u56FE\u4F20";
    }
    renderOperationFeedback("stream-start", view.streamDeviceId, feedbackDeviceEpoch(view, view.streamDeviceId));
    renderOperationFeedback("stream-stop", view.streamDeviceId, feedbackDeviceEpoch(view, view.streamDeviceId));
    for (const action of ["photo-capture", "photo-fetch"]) {
      const button = document.querySelector(`button[data-action="${action}"]`);
      if (!(button instanceof HTMLButtonElement)) continue;
      const decision = OperatorConsole.evaluate(action, view);
      button.disabled = !decision.ok;
      button.title = decision.ok ? button.textContent ?? "" : decision.reason ?? "\u5F53\u524D\u72B6\u6001\u4E0D\u5141\u8BB8\u6B64\u64CD\u4F5C";
      renderOperationFeedback(action, view.streamDeviceId, feedbackDeviceEpoch(view, view.streamDeviceId));
    }
    renderFlightPhotoProgress(view);
  }
  function renderFlightPhotoProgress(view) {
    const progress = photoProgressView(photoProgressCache.get(photoDisplayDeviceId(view)), Date.now());
    const pending = actionRequests.pending(view.streamDeviceId, "photo-fetch");
    renderPhotoProgress(el("flight-photo-progress"), progress, pending ? "\u6B63\u5728\u8BF7\u6C42\u7167\u7247\u56DE\u4F20\uFF0C\u7B49\u5F85\u8FDB\u5EA6" : view.streamDeviceId === null ? "\u8BF7\u5148\u9009\u62E9\u56FE\u4F20\u624B\u673A" : "\u5C1A\u672A\u5F00\u59CB\u56DE\u4F20\u7167\u7247");
  }
  function renderFlight(view) {
    if (view.streamSourceUnavailable) detachVideo();
    const devices = view.devices;
    const fill = (id, selected, onChange, offlineIds = []) => {
      const select = el(id);
      select.replaceChildren(...[
        Object.assign(document.createElement("option"), { value: "", textContent: "\u672A\u9009\u62E9" }),
        ...devices.map((device) => Object.assign(document.createElement("option"), {
          value: String(device.deviceId),
          textContent: callSign(String(device.deviceId)),
          selected: device.deviceId === selected
        })),
        ...offlineIds.map((deviceId) => Object.assign(document.createElement("option"), {
          value: deviceId,
          textContent: `${callSign(deviceId)}\uFF08\u79BB\u7EBF \xB7 \u6700\u8FD1\u56DE\u4F20\uFF09`,
          selected: deviceId === selected
        }))
      ]);
      select.onchange = () => {
        onChange(select.value || "");
        void render();
      };
    };
    fill("mission-select", view.missionDeviceId, (value) => {
      state.missionDeviceId = value.length > 0 ? value : null;
    });
    fill("direct-flight-select", view.missionDeviceId, (value) => {
      state.missionDeviceId = value.length > 0 ? value : null;
    });
    const offlinePhotoIds = photoProgressCache.deviceIds().filter((deviceId) => !devices.some((device) => device.deviceId === deviceId));
    fill("stream-select", photoDisplayDeviceId(view), (value) => {
      state.streamDeviceId = value.length > 0 ? value : null;
      photoProgressDeviceId = state.streamDeviceId;
    }, offlinePhotoIds);
    renderFlightControllerVideoHint(view);
    renderFlightPanelStatus(view);
    renderFlightPanelVisibility();
    renderMissionButtons(view);
    renderStreamButtons(view);
    armedCommand = expireArm(armedCommand, Date.now());
    if (armedCommand === null) clearArmTimer();
    for (const action of ["flight-takeoff", "flight-land", "flight-confirm-landing", "flight-return-home", "flight-stop-takeoff", "flight-stop-auto-landing"]) {
      const button = document.querySelector(`button[data-action="${action}"]`);
      if (!(button instanceof HTMLButtonElement)) continue;
      const decision = OperatorConsole.evaluate(action, view);
      button.disabled = !decision.ok;
      if (isArmableAction(action)) {
        button.textContent = buttonLabel(action, armedCommand);
        button.dataset.arm = armedCommand?.action === action ? "armed" : "idle";
      }
      button.title = decision.ok ? button.textContent ?? "" : decision.reason ?? "\u5F53\u524D\u72B6\u6001\u4E0D\u5141\u8BB8\u6B64\u64CD\u4F5C";
      renderOperationFeedback(action, view.missionDeviceId, feedbackDeviceEpoch(view, view.missionDeviceId));
    }
    const landingDevice = devices.find((device) => device.deviceId === view.missionDeviceId);
    void landingDevice;
    pendingFlightConfirmation = adoptPendingConfirmation(pendingFlightConfirmation, view.confirmation, Date.now());
  }
  var paintMonitorFlightHint = (slot, device, deviceId) => {
    const hint = slot.querySelector(".monitor-fc-hint");
    if (!(hint instanceof HTMLElement)) return;
    if (deviceId !== null && read4(connectionOf(device), "flightControllerHasConnectedOnce") !== false) monitorFlightControllerHints.delete(deviceId);
    const message = deviceId === null ? void 0 : monitorFlightControllerHints.get(deviceId);
    hint.hidden = message === void 0;
    hint.textContent = message ?? "";
  };
  var paintMonitorStreamButton = (slot, device, deviceId) => {
    const button = slot.querySelector("[data-monitor-stream]");
    if (!(button instanceof HTMLButtonElement)) return;
    const connection = connectionOf(device);
    const msdk = read4(connection, "msdk");
    const toggle = monitorStreamToggle({
      present: device !== void 0 && deviceId !== null,
      streamPhase: text4(read4(read4(device, "stream"), "phase")),
      videoPhase: text4(read4(read4(device, "video"), "phase")),
      failureCode: text4(read4(read4(device, "stream"), "failureCode")),
      sdkReady: msdk === void 0 ? read4(connection, "sdk") === "ready" : msdk === "ready",
      airLink: text4(read4(connection, "airLink")),
      camera: text4(read4(connection, "camera")),
      playing: deviceId !== null && [...monitorPlayers.values()].some((lane) => lane.deviceId === deviceId) && !(text4(read4(read4(device, "stream"), "phase")) === "failed" && text4(read4(read4(device, "stream"), "failureCode")) === "SOURCE_UNAVAILABLE"),
      phoneStreaming: read4(read4(connection, "live"), "streaming") === true,
      releasePhoneHold: deviceId !== null && monitorStreamHoldCleared.has(deviceId)
    });
    if (deviceId !== null && read4(read4(connection, "live"), "streaming") !== true) monitorStreamHoldCleared.delete(deviceId);
    button.textContent = toggle.label;
    button.disabled = !toggle.enabled;
    button.title = toggle.title;
    button.dataset.monitorStreamMode = toggle.mode;
  };
  var paintMonitorRouteButton = (slot, device, deviceId) => {
    const button = slot.querySelector("[data-monitor-route]");
    if (!(button instanceof HTMLButtonElement)) return;
    if (deviceId === null || device == null) {
      button.textContent = "\u822A\u7EBF\u9009\u62E9";
      button.disabled = true;
      button.title = "\u7A7A\u4F4D";
      button.dataset.monitorRouteMode = "choose";
      return;
    }
    const plan = monitorRoutePlans.get(deviceId);
    const sequenceBusy = plan?.button === "busy";
    const actionBusy = monitorRouteActionBusy.has(deviceId);
    const picking = !sequenceBusy && plan !== void 0 && monitorRailKind === "route" && monitorRailDeviceId === deviceId && plan.rail === "pick";
    const control = monitorRouteControl({
      missionPhase: text4(read4(read4(device, "mission"), "phase")),
      routeExecutionStarted: read4(read4(device, "mission"), "routeExecutionStarted") === true,
      missionExecution: text4(read4(connectionOf(device), "missionExecution")),
      djiMissionState: text4(read4(connectionOf(device), "missionDjiExecutionState"))
    });
    const sequenceFailed = plan !== void 0 && (plan.phone === "failed" || plan.aircraft === "failed" || plan.execute === "failed");
    const live = control === "await" && sequenceFailed ? "choose" : control;
    if (live !== "choose" && plan !== void 0 && monitorRailKind === "route" && monitorRailDeviceId === deviceId && plan.rail === "pick") {
      plan.rail = "status";
    }
    if (sequenceBusy) {
      button.textContent = "\u4E00\u952E\u6267\u884C";
      button.disabled = true;
      button.title = "\u6B63\u5728\u4E0A\u4F20\u5E76\u6267\u884C\u822A\u7EBF";
      button.dataset.monitorRouteMode = "busy";
      return;
    }
    if (live === "stopping") {
      button.textContent = "\u6B63\u5728\u505C\u6B62\u822A\u7EBF";
      button.disabled = true;
      button.title = "\u6B63\u5728\u505C\u6B62\u822A\u7EBF";
      button.dataset.monitorRouteMode = "stopping";
      return;
    }
    if (live === "pause" || live === "resume") {
      button.textContent = live === "pause" ? "\u6682\u505C\u822A\u7EBF" : "\u7EE7\u7EED\u6267\u884C";
      button.disabled = actionBusy;
      button.title = live === "pause" ? "\u6682\u505C\u822A\u7EBF\uFF0C\u98DE\u673A\u60AC\u505C\uFF0C\u4E4B\u540E\u53EF\u4EE5\u7EE7\u7EED" : "\u4ECE\u6682\u505C\u5904\u7EE7\u7EED\u6267\u884C\u822A\u7EBF";
      button.dataset.monitorRouteMode = live;
      return;
    }
    if (live === "await") {
      button.textContent = "\u7B49\u5F85\u8FDB\u5165\u822A\u7EBF";
      button.disabled = true;
      button.title = "\u98DE\u673A\u8FD8\u6CA1\u8FDB\u5165\u822A\u7EBF\u3002\u8981\u6536\u6389\u4EFB\u52A1\u8BF7\u70B9\u7D27\u6025\u60AC\u505C";
      button.dataset.monitorRouteMode = "await";
      return;
    }
    const chosen = picking && plan.routeId !== null;
    button.textContent = chosen ? "\u4E00\u952E\u6267\u884C" : picking ? "\u9000\u51FA\u9009\u62E9" : "\u822A\u7EBF\u9009\u62E9";
    button.dataset.monitorRouteMode = chosen ? "execute" : picking ? "exit" : "choose";
    button.disabled = false;
    button.title = button.textContent ?? "";
  };
  var paintMonitorPhotoButton = (slot, view, deviceId) => {
    const button = slot.querySelector("[data-monitor-photo]");
    if (!(button instanceof HTMLButtonElement)) return;
    if (deviceId === null) {
      button.disabled = true;
      button.title = "\u7A7A\u4F4D";
      button.textContent = "\u56DE\u4F20\u7167\u7247";
      return;
    }
    const decision = OperatorConsole.evaluate("photo-fetch", { ...view, workspace: "flight", streamDeviceId: deviceId });
    const progress = photoProgressView(photoProgressCache.get(deviceId), Date.now());
    const busy = monitorPhotoBusy.has(deviceId) || actionRequests.reason(deviceId, "photo-fetch") !== null;
    button.disabled = busy || !decision.ok;
    button.textContent = progress?.active === true ? "\u56DE\u4F20\u4E2D" : "\u56DE\u4F20\u7167\u7247";
    button.title = progress?.active === true ? `${progress.heading} \xB7 ${progress.count}` : decision.ok ? "\u56DE\u4F20\u7167\u7247" : decision.reason ?? "\u5F53\u524D\u72B6\u6001\u4E0D\u5141\u8BB8\u6B64\u64CD\u4F5C";
  };
  var paintMonitorHoldButton = (slot, deviceId) => {
    const button = slot.querySelector("[data-monitor-hold]");
    if (!(button instanceof HTMLButtonElement)) return;
    const busy = deviceId !== null && monitorHoldBusy.has(deviceId);
    button.disabled = deviceId === null || busy;
    button.title = deviceId === null ? "\u7A7A\u4F4D" : "\u822A\u7EBF\u8FDB\u884C\u4E2D\u5C06\u505C\u6B62\u822A\u7EBF\uFF1B\u5176\u4ED6\u65F6\u5019\u6309\u5F53\u524D\u98DE\u884C\u52A8\u4F5C\u5239\u8F66\u5E76\u60AC\u505C";
  };
  var paintMonitorDirectButtons = (slot, device, deviceId) => {
    const home = slot.querySelector("[data-monitor-home]");
    const land = slot.querySelector("[data-monitor-land]");
    if (!(home instanceof HTMLButtonElement) || !(land instanceof HTMLButtonElement)) return;
    if (deviceId === null || device == null) {
      home.textContent = "\u8FD4\u822A";
      land.textContent = "\u964D\u843D";
      home.disabled = true;
      land.disabled = true;
      home.title = "\u7A7A\u4F4D";
      land.title = "\u7A7A\u4F4D";
      home.dataset.monitorHomeMode = "return-home";
      land.dataset.monitorLandMode = "land";
      return;
    }
    const facts = monitorFactsOf(device);
    const direct = monitorDirectFlight({
      flying: facts.flying,
      motorsOn: facts.motorsOn,
      flightMode: facts.flightMode,
      landingConfirmationNeeded: facts.landingConfirmationNeeded,
      lowBatteryRthState: facts.lowBatteryRthState
    });
    home.textContent = direct.home.label;
    land.textContent = direct.land.label;
    home.disabled = monitorHomeBusy.has(deviceId);
    land.disabled = monitorLandBusy.has(deviceId);
    home.title = direct.home.label;
    land.title = direct.land.label;
    home.dataset.monitorHomeMode = direct.home.mode;
    land.dataset.monitorLandMode = direct.land.mode;
  };
  function paintMonitorActionRail(rail, deviceId, title, progress, doneLabel = "\u5DF2\u5B8C\u6210") {
    const head = document.createElement("div");
    head.className = "monitor-rail-head";
    const heading = document.createElement("strong");
    heading.textContent = callSign(deviceId);
    const subtitle = document.createElement("span");
    subtitle.textContent = title;
    head.append(heading, subtitle);
    const body = document.createElement("div");
    body.className = "monitor-rail-body";
    const row = document.createElement("div");
    row.className = progress.state === "done" ? "monitor-step is-done" : progress.state === "failed" ? "monitor-step is-failed" : "monitor-step";
    const label = document.createElement("span");
    label.textContent = title;
    const value = document.createElement("small");
    value.textContent = progress.state === "done" ? doneLabel : monitorRouteStepText[progress.state];
    row.append(label, value);
    body.append(row);
    if (progress.detail !== null) {
      const note = document.createElement("p");
      note.className = "monitor-rail-note";
      note.textContent = progress.detail;
      body.append(note);
    }
    rail.append(head, body);
  }
  function renderMonitorRail(view) {
    const rail = document.querySelector(".monitor-rail-panel");
    if (!(rail instanceof HTMLElement)) return;
    const deviceId = monitorRailDeviceId;
    const plan = deviceId === null ? void 0 : monitorRoutePlans.get(deviceId);
    if (deviceId !== null && monitorRailKind === "photo") {
      const progress = photoProgressView(photoProgressCache.get(deviceId), Date.now());
      const request = monitorPhotoProgress.get(deviceId);
      if (progress !== null || request !== void 0) {
        let host = rail.querySelector("[data-monitor-photo-progress]");
        if (rail.dataset.photoDevice !== deviceId || host === null) {
          const head2 = document.createElement("div");
          head2.className = "monitor-rail-head";
          const heading = document.createElement("strong");
          heading.textContent = callSign(deviceId);
          const subtitle = document.createElement("span");
          subtitle.textContent = "\u56DE\u4F20\u7167\u7247";
          head2.append(heading, subtitle);
          const body2 = document.createElement("div");
          body2.className = "monitor-rail-body";
          host = document.createElement("div");
          host.dataset.monitorPhotoProgress = "";
          body2.append(host);
          rail.replaceChildren(head2, body2);
          rail.dataset.photoDevice = deviceId;
        }
        renderPhotoProgress(host, progress, request?.detail ?? "\u6B63\u5728\u8BF7\u6C42\u7167\u7247\u56DE\u4F20\uFF0C\u7B49\u5F85\u8FDB\u5EA6");
        return;
      }
    }
    delete rail.dataset.photoDevice;
    rail.replaceChildren();
    if (deviceId !== null && monitorRailKind === "hold") {
      const hold = monitorHoldProgress.get(deviceId);
      if (hold !== void 0) {
        paintMonitorActionRail(rail, deviceId, "\u7D27\u6025\u60AC\u505C", hold);
        return;
      }
    }
    if (deviceId !== null && monitorRailKind === "direct") {
      const direct = monitorDirectProgress.get(deviceId);
      if (direct !== void 0) {
        paintMonitorActionRail(rail, deviceId, direct.title, direct, "\u5DF2\u53D7\u7406");
        return;
      }
    }
    if (deviceId !== null && monitorRailKind === "stream") {
      const stream = monitorStreamProgress.get(deviceId);
      if (stream !== void 0) {
        paintMonitorActionRail(rail, deviceId, "\u56FE\u4F20", stream);
        const hint = monitorFlightControllerHints.get(deviceId);
        const body2 = rail.querySelector(".monitor-rail-body");
        if (hint !== void 0 && body2 instanceof HTMLElement) {
          const banner = document.createElement("p");
          banner.className = "monitor-fc-hint";
          banner.setAttribute("role", "status");
          banner.textContent = hint;
          body2.append(banner);
        }
        return;
      }
    }
    if (deviceId !== null && monitorRailKind === "mission") {
      const mission = monitorMissionProgress.get(deviceId);
      if (mission !== void 0) {
        paintMonitorActionRail(rail, deviceId, mission.title, mission);
        return;
      }
    }
    if (deviceId === null || plan === void 0) return;
    const route = plan.routeId === null ? void 0 : view.routes.find((item) => item.routeId === plan.routeId);
    const head = document.createElement("div");
    head.className = "monitor-rail-head";
    const title = document.createElement("strong");
    title.textContent = callSign(deviceId);
    head.append(title);
    if (plan.rail === "status" && route !== void 0) {
      const subtitle = document.createElement("span");
      subtitle.textContent = route.displayName;
      head.append(subtitle);
    } else if (plan.rail === "pick") {
      const subtitle = document.createElement("span");
      subtitle.textContent = "\u9009\u62E9\u8981\u6267\u884C\u7684\u822A\u7EBF";
      head.append(subtitle);
    }
    const body = document.createElement("div");
    body.className = "monitor-rail-body";
    if (plan.rail === "pick") {
      const routes = view.routes.filter((item) => item.executable);
      if (routes.length === 0) {
        const empty = document.createElement("p");
        empty.className = "monitor-rail-empty";
        empty.textContent = "\u8FD8\u6CA1\u6709\u53EF\u6267\u884C\u7684\u822A\u7EBF";
        body.append(empty);
      }
      for (const item of routes) {
        const choice = document.createElement("button");
        choice.type = "button";
        choice.className = item.routeId === plan.routeId ? "monitor-route is-selected" : "monitor-route";
        choice.dataset.monitorRouteId = item.routeId;
        choice.textContent = item.displayName;
        body.append(choice);
      }
    } else {
      const steps = [
        ["\u4E0A\u4F20\u5230\u624B\u673A", plan.phone],
        ["\u4E0A\u4F20\u5230\u98DE\u673A", plan.aircraft],
        ["\u6267\u884C\u822A\u7EBF", plan.execute]
      ];
      for (const [name, state2] of steps) {
        const row = document.createElement("div");
        row.className = state2 === "done" ? "monitor-step is-done" : state2 === "failed" ? "monitor-step is-failed" : "monitor-step";
        const label = document.createElement("span");
        label.textContent = name;
        const value = document.createElement("small");
        value.textContent = monitorRouteStepText[state2];
        row.append(label, value);
        body.append(row);
      }
      if (plan.failure !== null) {
        const note = document.createElement("p");
        note.className = "monitor-rail-note";
        note.textContent = plan.failure;
        body.append(note);
      }
    }
    rail.append(head, body);
  }
  var applyMonitorView = () => {
    const workspace = document.querySelector("#workspace-monitor");
    if (workspace instanceof HTMLElement) workspace.classList.toggle("is-single", monitorViewMode === "single");
    document.querySelectorAll("[data-monitor-mode]").forEach((button) => {
      const selected = button.dataset.monitorMode === monitorViewMode;
      button.classList.toggle("is-selected", selected);
      button.setAttribute("aria-pressed", selected ? "true" : "false");
    });
    document.querySelectorAll("[data-monitor-slot]").forEach((slot) => {
      slot.classList.toggle("is-focused", Number(slot.dataset.monitorSlot) === monitorSingleSlot);
    });
  };
  function renderMonitor(view) {
    const devices = monitorDevices(view);
    document.querySelectorAll("[data-monitor-slot]").forEach((slot) => {
      const index = Number(slot.dataset.monitorSlot);
      const device = Number.isInteger(index) ? devices[index] : void 0;
      const name = slot.querySelector("[data-monitor-name]");
      const stateNode = slot.querySelector("[data-monitor-state]");
      const detailNode = slot.querySelector("[data-monitor-detail]");
      const setHud = (key, value) => {
        const node = slot.querySelector(`[data-monitor-hud="${key}"]`);
        if (node instanceof HTMLElement) node.textContent = value;
      };
      if (device === void 0) {
        if (name instanceof HTMLElement) name.textContent = "\u7A7A\u4F4D";
        if (stateNode instanceof HTMLElement) stateNode.textContent = "\u672A\u786E\u8BA4";
        if (detailNode instanceof HTMLElement) detailNode.textContent = "";
        for (const key of ["flying", "motors", "battery", "altitude", "gps", "mode"]) setHud(key, EMPTY_STATUS);
        paintMonitorStreamButton(slot, void 0, null);
        paintMonitorRouteButton(slot, void 0, null);
        paintMonitorPhotoButton(slot, view, null);
        paintMonitorHoldButton(slot, null);
        paintMonitorDirectButtons(slot, void 0, null);
        paintMonitorFlightHint(slot, void 0, null);
        return;
      }
      const deviceId = text4(read4(device, "deviceId"));
      if (name instanceof HTMLElement) name.textContent = deviceId === null ? "\u7A7A\u4F4D" : callSign(deviceId);
      const connection = connectionOf(device);
      const flying = text4(read4(connection, "flightState"));
      const motorsRaw = read4(connection, "motorsOn");
      const motorsOn = motorsRaw === true ? true : motorsRaw === false ? false : null;
      const pose = read4(connection, "pose");
      const status = monitorAircraftStatus(monitorFactsOf(device));
      if (stateNode instanceof HTMLElement) stateNode.textContent = status.label;
      if (detailNode instanceof HTMLElement) detailNode.textContent = status.detail;
      setHud("flying", hudFlying(flying));
      setHud("motors", hudMotors(motorsOn));
      setHud("battery", hudBattery(read4(connection, "batteryPercent")));
      setHud("altitude", hudAltitude(read4(pose, "altitudeMeters")));
      setHud("gps", hudText(read4(connection, "gpsSignalLevel")));
      setHud("mode", hudText(read4(connection, "flightMode")));
      paintMonitorStreamButton(slot, device, deviceId);
      paintMonitorRouteButton(slot, device, deviceId);
      paintMonitorPhotoButton(slot, view, deviceId);
      paintMonitorHoldButton(slot, deviceId);
      paintMonitorDirectButtons(slot, device, deviceId);
      paintMonitorFlightHint(slot, device, deviceId);
    });
    applyMonitorView();
    renderMonitorRail(view);
  }
  var deepUnwrap = (value, key) => {
    let current = value;
    for (let step = 0; step < 4; step += 1) {
      if (read4(current, key) !== void 0) return current;
      const next = unwrap2(current);
      if (next === current) return current;
      current = next;
    }
    return current;
  };
  var previewGeometry = (value) => {
    const current = deepUnwrap(value, "polyline");
    const polyline = read4(current, "polyline");
    const startMarker = read4(current, "startMarker");
    const endMarker = read4(current, "endMarker");
    if (!Array.isArray(polyline) || polyline.length < 2 || startMarker === void 0 || endMarker === void 0) return null;
    const points = polyline.flatMap((item) => {
      const longitude = read4(item, "longitude");
      const latitude = read4(item, "latitude");
      const altitude = read4(item, "altitude");
      return typeof longitude === "number" && typeof latitude === "number" ? [{ longitude, latitude, altitude: typeof altitude === "number" ? altitude : null }] : [];
    });
    const marker = (source) => {
      const longitude = read4(source, "longitude");
      const latitude = read4(source, "latitude");
      const altitude = read4(source, "altitude");
      return typeof longitude === "number" && typeof latitude === "number" ? { longitude, latitude, altitude: typeof altitude === "number" ? altitude : null } : null;
    };
    const start = marker(startMarker);
    const end = marker(endMarker);
    return points.length >= 2 && start !== null && end !== null ? { polyline: points, startMarker: start, endMarker: end } : null;
  };
  var settledInvoke = async (name, input, signal) => {
    try {
      return await awaitCurrentRender(bridge().invoke(name, input), signal);
    } catch {
      return void 0;
    }
  };
  async function fetchOnce(signal) {
    const snapshotTask = settledInvoke("state-snapshot", void 0, signal);
    const hintTask = settledInvoke("network-hint", void 0, signal);
    const streamTask = settledInvoke("stream-refresh", void 0, signal);
    const [snapshotResult, hintResult] = await Promise.all([snapshotTask, hintTask, streamTask]);
    if (signal.aborted) return;
    const snapshot2 = unwrap2(unwrap2(snapshotResult));
    if (snapshot2 !== void 0 && snapshot2 !== null) lastSnapshot = snapshot2;
    const listed = read4(unwrap2(hintResult), "hints");
    const liveHints = Array.isArray(listed) ? listed.filter((item) => typeof item === "string" && item.startsWith("ws://")) : [];
    if (liveHints.length > 0) lastRelayHint = liveHints.join(" \u6216 ");
    const view = projectCached();
    state.missionDeviceId = view.missionDeviceId;
    state.streamDeviceId = view.streamDeviceId;
    const routeId = view.selectedRoute?.routeId ?? null;
    if (state.workspace === "routes" && routeId !== null && lastRoutePreview?.routeId !== routeId) {
      const preview = previewGeometry(await settledInvoke("route-preview", { routeId }, signal));
      if (preview !== null) lastRoutePreview = { routeId, preview };
    }
  }
  function applyRouteMap(view) {
    const visible = state.workspace === "routes";
    setRouteMapVisible(visible);
    if (!visible) return;
    void ensureRouteMap(el("map")).then(() => {
      if (state.workspace !== "routes") {
        setRouteMapVisible(false);
        return;
      }
      setRouteMapVisible(true);
      el("map-notice").textContent = routeMapNotice();
      const routeId = view.selectedRoute?.routeId ?? null;
      if (routeId === null) {
        if (drawnPreviewId() !== null) clearRoutePreview();
        return;
      }
      const cached = lastRoutePreview?.routeId === routeId ? lastRoutePreview.preview : null;
      if (cached === null) {
        el("route-summary").textContent = `${view.selectedRoute?.displayName ?? ""} \xB7 ${routeMapNotice()}`;
        return;
      }
      if (drawnPreviewId() !== routeId) showRoutePreview(routeId, cached);
      el("map-notice").textContent = routeMapNotice();
      el("route-summary").textContent = `${view.selectedRoute?.displayName ?? ""} \xB7 ${cached.polyline.length} \u4E2A\u822A\u70B9`;
    }).catch((error) => {
      console.error("[sky-render]", error);
    });
  }
  var paintOnce = () => {
    document.querySelectorAll("nav button").forEach((button) => {
      button.classList.toggle("active", button.dataset.workspace === state.workspace);
    });
    document.querySelectorAll("main").forEach((node) => {
      node.classList.toggle("active", node.id === `workspace-${state.workspace}`);
    });
    const view = projectCached();
    state.missionDeviceId = view.missionDeviceId;
    state.streamDeviceId = view.streamDeviceId;
    try {
      renderDevices(view);
    } catch (error) {
      console.error("[sky-render]", error);
    }
    try {
      renderRoutes(view);
    } catch (error) {
      console.error("[sky-render]", error);
    }
    try {
      renderFlight(view);
    } catch (error) {
      console.error("[sky-render]", error);
    }
    try {
      renderMonitor(view);
    } catch (error) {
      console.error("[sky-render]", error);
    }
    renderActionRequests(view);
    try {
      applyRouteMap(view);
    } catch (error) {
      console.error("[sky-render]", error);
    }
    if (state.workspace === "monitor") void ensureMonitorPlayback(view);
    else {
      monitorPlaybackEpoch += 1;
      releaseMonitorPlayers();
      void ensurePlayback(view);
    }
  };
  var paintScheduler = createRenderScheduler(async () => {
    paintOnce();
  }, { deadlineMs: 6e4 });
  var snapshotRefresh = createBackgroundRefresh(fetchOnce, {
    deadlineMs: 5e3,
    intervalMs: 1e3,
    onFetched: () => {
      void paintScheduler.request();
    }
  });
  var photoClockTimer = window.setInterval(() => {
    const deviceId = state.workspace === "monitor" && monitorRailKind === "photo" ? monitorRailDeviceId : state.workspace === "flight" && state.flightPanel === "stream" ? photoProgressDeviceId : null;
    if (deviceId === null) return;
    projectCached();
    const progress = photoProgressView(photoProgressCache.get(deviceId), Date.now());
    if (progress?.active === true) void paintScheduler.request();
  }, 1e3);
  window.addEventListener("beforeunload", () => {
    window.clearInterval(photoClockTimer);
  });
  var render = async () => {
    try {
      await paintScheduler.request();
    } catch (error) {
      console.error("[sky-render]", error);
      renderFlightConfirmationFallback();
      renderMissionStartConfirmationFallback();
    }
    void snapshotRefresh.request();
  };
  document.querySelectorAll("nav button").forEach((button) => {
    button.addEventListener("click", () => {
      const workspace = button.dataset.workspace;
      if (workspace === "monitor" && state.workspace === "flight" && state.flightPanel === "stream") {
        const view = projectCached();
        const deviceId = photoDisplayDeviceId(view);
        if (deviceId !== null && photoProgressView(photoProgressCache.get(deviceId), Date.now()) !== null) {
          monitorRailDeviceId = deviceId;
          monitorRailKind = "photo";
        }
      }
      if (workspace === "devices" || workspace === "routes" || workspace === "flight" || workspace === "monitor") state.workspace = workspace;
      void render();
    });
  });
  document.querySelectorAll("[data-flight-panel]").forEach((button) => {
    button.addEventListener("click", () => {
      const panel = button.dataset.flightPanel;
      if (panel !== "stream" && panel !== "mission" && panel !== "direct-flight") return;
      if (panel !== state.flightPanel && armedCommand !== null) void expireArmedCommand();
      state.flightPanel = panel;
      void render();
    });
  });
  for (const buttonId of ["pairing-start", "pairing-stop"]) {
    el(buttonId).addEventListener("click", async () => {
      const view = projectCached();
      const deviceId = view.missionDeviceId;
      const pairing = pairingControl(view);
      if (deviceId === null || pairingCommandInFlight !== null || pairing.stopping) return;
      const action = pairing.action;
      pairingCommandInFlight = { deviceId, action };
      try {
        show(action === "pairing-start" ? "\u6B63\u5728\u8BF7\u6C42\u5F00\u59CB\u5BF9\u9891" : "\u6B63\u5728\u8BF7\u6C42\u505C\u6B62\u5BF9\u9891");
        void render().catch(() => void 0);
        await run(action, action, { deviceId }, view);
      } catch {
        show("\u5BF9\u9891\u64CD\u4F5C\u7ED3\u679C\u5C1A\u672A\u786E\u8BA4\uFF0C\u8BF7\u67E5\u770B\u8BBE\u5907\u72B6\u6001\u540E\u518D\u64CD\u4F5C");
      } finally {
        pairingCommandInFlight = null;
        void snapshotRefresh.request();
        try {
          await render();
        } catch {
        }
      }
    });
  }
  el("device-refresh").addEventListener("click", async () => {
    const view = await projectView();
    const deviceId = view.missionDeviceId;
    if (deviceId === null) {
      show("\u8BF7\u5148\u9009\u62E9\u5DF2\u8FDE\u63A5\u7684\u624B\u673A");
      return;
    }
    const button = el("device-refresh");
    button.disabled = true;
    try {
      const result = unwrap2(await bridge().invoke("device-refresh", { deviceId }));
      show(accepted(result) ? "\u5DF2\u8BFB\u53D6\u5F53\u524D\u624B\u673A\u72B6\u6001\uFF0C\u8BF7\u67E5\u770B\u5404\u9879\u72B6\u6001" : operatorNotice(result));
    } catch {
      show("\u72B6\u6001\u5237\u65B0\u5931\u8D25\uFF0C\u8BF7\u68C0\u67E5\u624B\u673A\u8FDE\u63A5\u540E\u91CD\u8BD5");
    } finally {
      await render();
    }
  });
  el("device-link-measure").addEventListener("click", async () => {
    const view = await projectView();
    const deviceId = view.missionDeviceId;
    if (deviceId === null) {
      show("\u8BF7\u5148\u9009\u62E9\u5DF2\u8FDE\u63A5\u7684\u624B\u673A");
      return;
    }
    if (phoneLinkProbeInFlightDeviceId !== null) {
      show("\u6B63\u5728\u6D4B\u91CF\u624B\u673A\u8FDE\u63A5\uFF0C\u8BF7\u7B49\u5F85\u672C\u6B21\u7ED3\u679C");
      return;
    }
    const selectedDevice = view.devices.find((device) => device.deviceId === deviceId);
    if (selectedDevice === void 0) {
      show("\u624B\u673A\u8FDE\u63A5\u5DF2\u53D8\u66F4\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5");
      return;
    }
    const connectionEpoch = connectionEpochOf(selectedDevice);
    phoneLinkProbeInFlightDeviceId = deviceId;
    try {
      await render();
      const report = phoneLinkProbeReport(deepUnwrap(await bridge().invoke("device-link-measure", { deviceId }), "status"));
      phoneLinkProbes.set(deviceId, Object.freeze({ connectionEpoch, report }));
      show(report.status === "measured" ? "\u5DF2\u5B8C\u6210\u624B\u673A\u8FDE\u63A5\u6D4B\u91CF\uFF0C\u8BF7\u67E5\u770B\u8FDE\u63A5\u72B6\u6001" : phoneLinkProbeLabel(report, false));
    } catch {
      const report = Object.freeze({ status: "unavailable", sampleCount: 0 });
      phoneLinkProbes.set(deviceId, Object.freeze({ connectionEpoch, report }));
      show("\u624B\u673A\u8FDE\u63A5\u6D4B\u91CF\u5931\u8D25\uFF0C\u8BF7\u68C0\u67E5\u4E2D\u7EE7\u8FDE\u63A5\u540E\u91CD\u8BD5");
    } finally {
      phoneLinkProbeInFlightDeviceId = null;
      try {
        await render();
      } catch {
      }
    }
  });
  el("route-import").addEventListener("click", async () => {
    const button = el("route-import");
    const view = await projectView();
    const decision = OperatorConsole.evaluate("import-route", view);
    if (!decision.ok) {
      blocked("import-route", decision.reason ?? "\u65E0\u6CD5\u5BFC\u5165");
      return;
    }
    const pick = bridge().selectRouteFile;
    if (pick === void 0) {
      show("\u5F53\u524D\u684C\u9762\u7A0B\u5E8F\u4E0D\u80FD\u9009\u62E9\u822A\u8FF9\u6587\u4EF6");
      return;
    }
    button.disabled = true;
    try {
      const selected = await pick();
      if (selected.ok !== true || typeof selected.fileName !== "string" || selected.bytes === void 0) return;
      const imported = deepUnwrap(await bridge().invoke("route-import", { fileName: selected.fileName, bytes: selected.bytes }), "status");
      const status = read4(imported, "status");
      const routeId = read4(read4(imported, "route"), "routeId");
      if (status !== "imported" || typeof routeId !== "string") {
        const error = read4(imported, "error");
        const detail = typeof read4(error, "message") === "string" ? read4(error, "message") : typeof read4(error, "code") === "string" ? `\uFF08${read4(error, "code")}\uFF09` : "";
        show(detail.length > 0 ? `\u822A\u7EBF\u5BFC\u5165\u5931\u8D25${detail.startsWith("\uFF08") ? detail : `\uFF1A${detail}`}` : "\u822A\u7EBF\u5BFC\u5165\u5931\u8D25\uFF0C\u8BF7\u786E\u8BA4\u662F Wayline \u5BFC\u51FA\u7684 KML/KMZ");
        await render();
        return;
      }
      await bridge().invoke("route-select", { routeId });
      show(read4(imported, "duplicate") === true ? "\u8BE5\u822A\u7EBF\u5DF2\u7ECF\u5BFC\u5165\u8FC7\uFF0C\u5DF2\u91CD\u65B0\u9009\u4E2D\u3002" : `\u5DF2\u5BFC\u5165 ${selected.fileName}`);
      await render();
    } finally {
      button.disabled = false;
    }
  });
  el("route-locate").addEventListener("click", () => {
    locateDrawnRoute();
  });
  el("route-select").addEventListener("click", (event) => {
    event.stopPropagation();
    setRouteMenuOpen(!routeMenuOpen);
  });
  document.addEventListener("click", (event) => {
    if (!routeMenuOpen) return;
    if (event.target instanceof Node && el("route-picker-wrap").contains(event.target)) return;
    setRouteMenuOpen(false);
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") setRouteMenuOpen(false);
  });
  el("route-remove").addEventListener("click", async () => {
    const view = await projectView();
    const routeId = view.selectedRoute?.routeId;
    if (routeId === void 0 || routeId === null) {
      show("\u8BF7\u5148\u9009\u62E9\u8981\u5220\u9664\u7684\u822A\u7EBF");
      return;
    }
    const decision = OperatorConsole.evaluate("remove-route", view);
    if (!decision.ok) {
      blocked("remove-route", decision.reason ?? "\u65E0\u6CD5\u5220\u9664");
      return;
    }
    const removed = unwrap2(await bridge().invoke("route-remove", { routeId }));
    if (accepted(removed) !== true && read4(removed, "ok") === false) {
      show("\u5F53\u524D\u822A\u7EBF\u65E0\u6CD5\u5220\u9664");
      await render();
      return;
    }
    clearRoutePreview();
    const remaining = await projectView();
    const next = remaining.routes.find((route) => route.routeId !== routeId);
    if (next !== void 0) await bridge().invoke("route-select", { routeId: next.routeId });
    show(next !== void 0 ? `\u5DF2\u5220\u9664\uFF0C\u5F53\u524D\u4E3A ${next.displayName}` : "\u5DF2\u5220\u9664\u5F53\u524D\u822A\u7EBF");
    await render();
  });
  var monitorStepSucceeded = (feedback) => feedback.outcome === "accepted" || feedback.outcome === "completed";
  async function runMonitorRoute(deviceId, routeId) {
    const plan = monitorRoutePlan(deviceId);
    const fail = async (step, message) => {
      plan[step] = "failed";
      plan.failure = message;
      plan.button = "choose";
      show(`${callSign(deviceId)}\uFF1A${message}`);
      try {
        await render();
      } catch {
      }
    };
    const paint = async () => {
      try {
        await render();
      } catch {
      }
    };
    let view;
    try {
      view = projectCached();
    } catch {
      await fail("phone", "\u754C\u9762\u8BFB\u53D6\u5931\u8D25\uFF0C\u672C\u6B21\u64CD\u4F5C\u672A\u53D1\u9001");
      return;
    }
    const route = view.routes.find((item) => item.routeId === routeId);
    if (route === void 0 || route.executable !== true) {
      await fail("phone", route?.blockedReason ?? "\u8FD9\u6761\u822A\u7EBF\u73B0\u5728\u4E0D\u80FD\u6267\u884C");
      return;
    }
    const epoch = feedbackDeviceEpoch(view, deviceId);
    try {
      const released = unwrap2(await bridge().invoke("mission-release", { deviceId }));
      if (accepted(released) !== true) {
        await fail("phone", captureFeedback("mission-release", deviceId, epoch, released).message);
        return;
      }
      const assigned = unwrap2(await bridge().invoke("assignment-assign", { deviceId, routeId }));
      if (accepted(assigned) !== true) {
        await fail("phone", "\u822A\u7EBF\u672A\u80FD\u8D4B\u7ED9\u8FD9\u67B6\u98DE\u673A");
        return;
      }
      const staged = await bridge().invoke("mission-stage", { deviceId });
      const stageFeedback = captureFeedback("mission-stage", deviceId, epoch, staged);
      if (!monitorStepSucceeded(stageFeedback)) {
        await fail("phone", stageFeedback.message);
        return;
      }
      plan.phone = "done";
      plan.aircraft = "running";
      await paint();
      const uploaded = await bridge().invoke("mission-upload", { deviceId });
      const uploadFeedback = captureFeedback("mission-upload", deviceId, epoch, uploaded);
      if (!monitorStepSucceeded(uploadFeedback)) {
        await fail("aircraft", uploadFeedback.message);
        return;
      }
      plan.aircraft = "done";
      plan.execute = "running";
      await paint();
      const started = await bridge().invoke("mission-start", { deviceId });
      const startFeedback = captureFeedback("mission-start", deviceId, epoch, started);
      if (!monitorStepSucceeded(startFeedback)) {
        await fail("execute", startFeedback.message);
        return;
      }
      plan.execute = "done";
      plan.failure = null;
      plan.button = "choose";
      show(`${callSign(deviceId)}\uFF1A${startFeedback.message}`);
      await paint();
    } catch {
      const step = plan.phone !== "done" ? "phone" : plan.aircraft !== "done" ? "aircraft" : "execute";
      await fail(step, "\u6682\u65F6\u65E0\u6CD5\u5B8C\u6210\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5");
    }
  }
  document.querySelector(".monitor-rail")?.addEventListener("click", (event) => {
    const choice = event.target instanceof Element ? event.target.closest("[data-monitor-route-id]") : null;
    const deviceId = monitorRailDeviceId;
    const routeId = choice?.dataset.monitorRouteId;
    if (choice === null || deviceId === null || routeId === void 0 || routeId.length === 0) return;
    const plan = monitorRoutePlan(deviceId);
    if (plan.rail !== "pick" || plan.button === "busy") return;
    plan.routeId = plan.routeId === routeId ? null : routeId;
    plan.button = plan.routeId === null ? "choose" : "execute";
    void render();
  });
  document.querySelectorAll("[data-monitor-route]").forEach((button) => {
    button.addEventListener("click", async () => {
      const tile = button.closest("[data-monitor-slot]");
      const slot = Number(tile instanceof HTMLElement ? tile.dataset.monitorSlot : "");
      if (!Number.isInteger(slot)) return;
      let view;
      try {
        view = projectCached();
      } catch {
        show("\u754C\u9762\u8BFB\u53D6\u5931\u8D25\uFF0C\u672C\u6B21\u64CD\u4F5C\u672A\u53D1\u9001\uFF1B\u8BF7\u786E\u8BA4\u624B\u673A\u4ECD\u8FDE\u63A5\u540E\u91CD\u8BD5");
        return;
      }
      const device = monitorDevices(view)[slot];
      const deviceId = device === void 0 ? null : text4(read4(device, "deviceId"));
      if (deviceId === null) return;
      const mode = button.dataset.monitorRouteMode;
      if (mode === "pause" || mode === "resume") {
        if (monitorRouteActionBusy.has(deviceId)) return;
        const action = mode === "pause" ? "mission-pause" : "mission-resume";
        const runningDetail = mode === "pause" ? "\u6B63\u5728\u6682\u505C\u822A\u7EBF" : "\u6B63\u5728\u7EE7\u7EED\u6267\u884C";
        const title = mode === "pause" ? "\u6682\u505C\u822A\u7EBF" : "\u7EE7\u7EED\u6267\u884C";
        monitorRouteActionBusy.add(deviceId);
        button.disabled = true;
        monitorMissionProgress.set(deviceId, { title, state: "running", detail: runningDetail });
        monitorRailDeviceId = deviceId;
        monitorRailKind = "mission";
        show(`${callSign(deviceId)}\uFF1A${runningDetail}`);
        try {
          await render();
        } catch {
        }
        try {
          const result = await bridge().invoke(action, { deviceId });
          const feedback = captureFeedback(action, deviceId, feedbackDeviceEpoch(view, deviceId), result);
          const illegal = text4(read4(unwrapAll(result), "code")) === "ILLEGAL_PHASE";
          const message = illegal ? mode === "pause" ? "\u5F53\u524D\u9636\u6BB5\u4E0D\u80FD\u6682\u505C" : "\u5F53\u524D\u9636\u6BB5\u4E0D\u80FD\u7EE7\u7EED" : feedback.message;
          const done = !illegal && monitorStepSucceeded(feedback);
          monitorMissionProgress.set(deviceId, { title, state: done ? "done" : "failed", detail: message });
          show(`${callSign(deviceId)}\uFF1A${message}`);
        } catch {
          monitorMissionProgress.set(deviceId, { title, state: "failed", detail: "\u6682\u65F6\u65E0\u6CD5\u5B8C\u6210\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5" });
          show(`${callSign(deviceId)}\uFF1A\u6682\u65F6\u65E0\u6CD5\u5B8C\u6210\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5`);
        } finally {
          monitorRouteActionBusy.delete(deviceId);
          try {
            await render();
          } catch {
          }
        }
        return;
      }
      if (mode === "await" || mode === "stopping" || mode === "busy") return;
      const plan = monitorRoutePlan(deviceId);
      if (plan.button === "busy") return;
      const picking = monitorRailKind === "route" && monitorRailDeviceId === deviceId && plan.rail === "pick";
      if (!picking) {
        plan.resumeStatus = monitorRailKind === "route" && plan.rail === "status";
        plan.resumePhoto = monitorRailKind === "photo" && (monitorPhotoProgress.has(deviceId) || photoProgressView(read4(device, "photo"), Date.now()) !== null);
        plan.resumeHold = monitorRailKind === "hold" && monitorHoldProgress.has(deviceId);
        plan.resumeDirect = monitorRailKind === "direct" && monitorDirectProgress.has(deviceId);
        plan.resumeStream = monitorRailKind === "stream" && monitorStreamProgress.has(deviceId);
        if (plan.routeId === null) {
          const assigned = text4(read4(read4(device, "assignment"), "routeId"));
          const known = assigned === null ? void 0 : view.routes.find((item) => item.routeId === assigned && item.executable);
          if (known !== void 0) plan.routeId = known.routeId;
        }
        plan.rail = "pick";
        monitorRailKind = "route";
        monitorRailDeviceId = deviceId;
        await render();
        return;
      }
      if (plan.routeId === null) {
        if (plan.resumeStatus) {
          plan.rail = "status";
          monitorRailKind = "route";
        } else if (plan.resumePhoto && (monitorPhotoProgress.has(deviceId) || photoProgressView(read4(device, "photo"), Date.now()) !== null)) {
          monitorRailKind = "photo";
        } else if (plan.resumeHold && monitorHoldProgress.has(deviceId)) {
          monitorRailKind = "hold";
        } else if (plan.resumeDirect && monitorDirectProgress.has(deviceId)) {
          monitorRailKind = "direct";
        } else if (plan.resumeStream && monitorStreamProgress.has(deviceId)) {
          monitorRailKind = "stream";
        } else monitorRailDeviceId = null;
        plan.resumeStatus = false;
        plan.resumePhoto = false;
        plan.resumeHold = false;
        plan.resumeDirect = false;
        plan.resumeStream = false;
        await render();
        return;
      }
      const routeId = plan.routeId;
      plan.button = "busy";
      plan.rail = "status";
      plan.phone = "running";
      plan.aircraft = "pending";
      plan.execute = "pending";
      plan.failure = null;
      monitorRailKind = "route";
      monitorRailDeviceId = deviceId;
      try {
        await render();
      } catch {
      }
      await runMonitorRoute(deviceId, routeId);
    });
  });
  document.querySelectorAll("[data-monitor-photo]").forEach((button) => {
    button.addEventListener("click", async () => {
      const tile = button.closest("[data-monitor-slot]");
      const slot = Number(tile instanceof HTMLElement ? tile.dataset.monitorSlot : "");
      if (!Number.isInteger(slot)) return;
      let view;
      try {
        view = projectCached();
      } catch {
        show("\u754C\u9762\u8BFB\u53D6\u5931\u8D25\uFF0C\u672C\u6B21\u64CD\u4F5C\u672A\u53D1\u9001\uFF1B\u8BF7\u786E\u8BA4\u624B\u673A\u4ECD\u8FDE\u63A5\u540E\u91CD\u8BD5");
        return;
      }
      const device = monitorDevices(view)[slot];
      const deviceId = device === void 0 ? null : text4(read4(device, "deviceId"));
      if (deviceId === null || monitorPhotoBusy.has(deviceId)) return;
      const decision = OperatorConsole.evaluate("photo-fetch", { ...view, workspace: "flight", streamDeviceId: deviceId });
      if (!decision.ok) {
        show(`${callSign(deviceId)}\uFF1A${decision.reason ?? "\u5F53\u524D\u72B6\u6001\u4E0D\u5141\u8BB8\u6B64\u64CD\u4F5C"}`);
        return;
      }
      const photoReservation = actionRequests.reserve(deviceId, "photo-fetch");
      if (!photoReservation.ok) {
        show(photoReservation.reason);
        return;
      }
      renderActionRequests(view);
      monitorPhotoBusy.add(deviceId);
      button.disabled = true;
      monitorPhotoProgress.set(deviceId, { state: "running", detail: "\u6B63\u5728\u8BF7\u6C42\u7167\u7247\u56DE\u4F20\uFF0C\u7B49\u5F85\u8FDB\u5EA6" });
      monitorRailDeviceId = deviceId;
      monitorRailKind = "photo";
      show(`${callSign(deviceId)}\uFF1A\u6B63\u5728\u8BF7\u6C42\u56DE\u4F20\u7535\u8111\u4E0A\u6CA1\u6709\u7684\u7167\u7247`);
      try {
        await render();
      } catch {
      }
      try {
        const result = await bridge().invoke("photo-fetch", { deviceId });
        const feedback = captureFeedback("photo-fetch", deviceId, feedbackDeviceEpoch(view, deviceId), result);
        const done = feedback.outcome === "accepted" || feedback.outcome === "completed";
        monitorPhotoProgress.set(deviceId, { state: done ? "done" : "failed", detail: feedback.message });
        show(`${callSign(deviceId)}\uFF1A${feedback.message}`);
      } catch {
        monitorPhotoProgress.set(deviceId, { state: "failed", detail: "\u6682\u65F6\u65E0\u6CD5\u5B8C\u6210\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5" });
        show(`${callSign(deviceId)}\uFF1A\u6682\u65F6\u65E0\u6CD5\u5B8C\u6210\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5`);
      } finally {
        photoReservation.release();
        monitorPhotoBusy.delete(deviceId);
        try {
          await render();
        } catch {
        }
      }
    });
  });
  var confirmMonitorFlight = async (deviceId, action) => {
    const requested = await bridge().invoke("flight-request", { deviceId, action });
    const confirmation = confirmationFromResult(requested, deviceId, action);
    if (confirmation === null || confirmation.deviceId !== deviceId || confirmation.action !== action) return requested;
    return bridge().invoke("flight-confirm", { deviceId, confirmationId: confirmation.confirmationId });
  };
  var monitorFactsOf = (device) => {
    const connection = connectionOf(device);
    const motorsRaw = read4(connection, "motorsOn");
    const waypoint = read4(connection, "currentWaypointIndex");
    return {
      flying: text4(read4(connection, "flightState")),
      motorsOn: motorsRaw === true ? true : motorsRaw === false ? false : null,
      flightMode: text4(read4(connection, "flightMode")),
      landingConfirmationNeeded: read4(connection, "landingConfirmationNeeded") === true ? true : read4(connection, "landingConfirmationNeeded") === false ? false : null,
      landingProtectionState: text4(read4(connection, "landingProtectionState")),
      lowBatteryRthState: text4(read4(connection, "lowBatteryRthState")),
      missionExecution: text4(read4(connection, "missionExecution")),
      djiMissionState: text4(read4(connection, "missionDjiExecutionState")),
      waypointIndex: typeof waypoint === "number" && Number.isSafeInteger(waypoint) && waypoint >= 0 ? waypoint : null,
      flightController: text4(read4(connection, "flightController"))
    };
  };
  document.querySelectorAll("[data-monitor-hold]").forEach((button) => {
    button.addEventListener("click", async () => {
      const tile = button.closest("[data-monitor-slot]");
      const slot = Number(tile instanceof HTMLElement ? tile.dataset.monitorSlot : "");
      if (!Number.isInteger(slot)) return;
      let view;
      try {
        view = projectCached();
      } catch {
        show("\u754C\u9762\u8BFB\u53D6\u5931\u8D25\uFF0C\u672C\u6B21\u64CD\u4F5C\u672A\u53D1\u9001\uFF1B\u8BF7\u786E\u8BA4\u624B\u673A\u4ECD\u8FDE\u63A5\u540E\u91CD\u8BD5");
        return;
      }
      const device = monitorDevices(view)[slot];
      const deviceId = device === void 0 ? null : text4(read4(device, "deviceId"));
      if (device === void 0 || deviceId === null || monitorHoldBusy.has(deviceId)) return;
      const command = monitorHoverCommand(monitorFactsOf(device));
      monitorHoldBusy.add(deviceId);
      button.disabled = true;
      monitorRailDeviceId = deviceId;
      monitorRailKind = "hold";
      if (command.kind === "none") {
        monitorHoldProgress.set(deviceId, { state: command.state, detail: command.detail });
        show(`${callSign(deviceId)}\uFF1A${command.detail}`);
        monitorHoldBusy.delete(deviceId);
        try {
          await render();
        } catch {
        }
        return;
      }
      monitorHoldProgress.set(deviceId, { state: "running", detail: command.detail });
      show(`${callSign(deviceId)}\uFF1A${command.detail}`);
      try {
        await render();
      } catch {
      }
      try {
        const result = command.kind === "stop" ? await bridge().invoke("mission-stop", { deviceId }) : await confirmMonitorFlight(deviceId, command.action);
        const feedbackAction = command.kind === "stop" ? "mission-stop" : `flight-${command.action}`;
        const feedback = captureFeedback(feedbackAction, deviceId, feedbackDeviceEpoch(view, deviceId), result);
        const code = text4(read4(unwrapAll(result), "code"));
        const message = command.kind === "stop" && code === "ILLEGAL_PHASE" ? "\u5F53\u524D\u9636\u6BB5\u4E0D\u80FD\u505C\u6B62\u822A\u7EBF" : feedback.message;
        const done = code !== "ILLEGAL_PHASE" && (feedback.outcome === "accepted" || feedback.outcome === "completed");
        monitorHoldProgress.set(deviceId, { state: done ? "done" : "failed", detail: message });
        show(`${callSign(deviceId)}\uFF1A${message}`);
      } catch {
        monitorHoldProgress.set(deviceId, { state: "failed", detail: "\u6682\u65F6\u65E0\u6CD5\u5B8C\u6210\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5" });
        show(`${callSign(deviceId)}\uFF1A\u6682\u65F6\u65E0\u6CD5\u5B8C\u6210\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5`);
      } finally {
        monitorHoldBusy.delete(deviceId);
        try {
          await render();
        } catch {
        }
      }
    });
  });
  var monitorDirectRunningDetail = (mode) => {
    if (mode === "return-home") return "\u6B63\u5728\u8FD4\u822A";
    if (mode === "stop-go-home") return "\u6B63\u5728\u505C\u6B62\u8FD4\u822A";
    if (mode === "confirm-landing") return "\u6B63\u5728\u786E\u8BA4\u964D\u843D";
    return "\u6B63\u5728\u964D\u843D";
  };
  var bindMonitorDirect = (selector, which) => {
    document.querySelectorAll(selector).forEach((button) => {
      button.addEventListener("click", async () => {
        const tile = button.closest("[data-monitor-slot]");
        const slot = Number(tile instanceof HTMLElement ? tile.dataset.monitorSlot : "");
        if (!Number.isInteger(slot)) return;
        let view;
        try {
          view = projectCached();
        } catch {
          show("\u754C\u9762\u8BFB\u53D6\u5931\u8D25\uFF0C\u672C\u6B21\u64CD\u4F5C\u672A\u53D1\u9001\uFF1B\u8BF7\u786E\u8BA4\u624B\u673A\u4ECD\u8FDE\u63A5\u540E\u91CD\u8BD5");
          return;
        }
        const device = monitorDevices(view)[slot];
        const deviceId = device === void 0 ? null : text4(read4(device, "deviceId"));
        const busy = which === "home" ? monitorHomeBusy : monitorLandBusy;
        if (device === void 0 || deviceId === null || busy.has(deviceId)) return;
        const facts = monitorFactsOf(device);
        const direct = monitorDirectFlight({
          flying: facts.flying,
          motorsOn: facts.motorsOn,
          flightMode: facts.flightMode,
          landingConfirmationNeeded: facts.landingConfirmationNeeded,
          lowBatteryRthState: facts.lowBatteryRthState
        });
        const control = which === "home" ? direct.home : direct.land;
        const detail = monitorDirectRunningDetail(control.mode);
        busy.add(deviceId);
        button.disabled = true;
        monitorDirectProgress.set(deviceId, { title: control.label, state: "running", detail });
        monitorRailDeviceId = deviceId;
        monitorRailKind = "direct";
        show(`${callSign(deviceId)}\uFF1A${detail}`);
        try {
          await render();
        } catch {
        }
        try {
          const result = await confirmMonitorFlight(deviceId, control.mode);
          const feedback = captureFeedback(`flight-${control.mode}`, deviceId, feedbackDeviceEpoch(view, deviceId), result);
          const done = feedback.outcome === "accepted" || feedback.outcome === "completed";
          monitorDirectProgress.set(deviceId, { title: control.label, state: done ? "done" : "failed", detail: feedback.message });
          show(`${callSign(deviceId)}\uFF1A${feedback.message}`);
        } catch {
          monitorDirectProgress.set(deviceId, { title: control.label, state: "failed", detail: "\u6682\u65F6\u65E0\u6CD5\u5B8C\u6210\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5" });
          show(`${callSign(deviceId)}\uFF1A\u6682\u65F6\u65E0\u6CD5\u5B8C\u6210\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5`);
        } finally {
          busy.delete(deviceId);
          try {
            await render();
          } catch {
          }
        }
      });
    });
  };
  bindMonitorDirect("[data-monitor-home]", "home");
  bindMonitorDirect("[data-monitor-land]", "land");
  document.querySelectorAll("[data-monitor-mode]").forEach((button) => {
    button.addEventListener("click", () => {
      const mode = button.dataset.monitorMode;
      if (mode !== "grid" && mode !== "single") return;
      if (mode === monitorViewMode) return;
      monitorViewMode = mode;
      applyMonitorView();
      void render();
    });
  });
  document.querySelectorAll("[data-monitor-step]").forEach((button) => {
    button.addEventListener("click", () => {
      if (monitorViewMode !== "single") return;
      const step = Number(button.dataset.monitorStep);
      if (step !== -1 && step !== 1) return;
      monitorSingleSlot = (monitorSingleSlot + step + 4) % 4;
      applyMonitorView();
      void render();
    });
  });
  document.querySelectorAll("[data-monitor-stream]").forEach((button) => {
    button.addEventListener("click", async () => {
      const tile = button.closest("[data-monitor-slot]");
      const slot = Number(tile instanceof HTMLElement ? tile.dataset.monitorSlot : "");
      if (!Number.isInteger(slot)) return;
      let view;
      try {
        view = projectCached();
      } catch {
        show("\u754C\u9762\u8BFB\u53D6\u5931\u8D25\uFF0C\u672C\u6B21\u64CD\u4F5C\u672A\u53D1\u9001\uFF1B\u8BF7\u786E\u8BA4\u624B\u673A\u4ECD\u8FDE\u63A5\u540E\u91CD\u8BD5");
        return;
      }
      const device = monitorDevices(view)[slot];
      const deviceId = device === void 0 ? null : text4(read4(device, "deviceId"));
      const phoneStreaming = read4(read4(connectionOf(device), "live"), "streaming") === true;
      const released = deviceId !== null && monitorStreamHoldCleared.has(deviceId);
      const mode = phoneStreaming && !released ? "stop" : button.dataset.monitorStreamMode;
      if (deviceId === null || mode !== "start" && mode !== "stop") {
        if (button.title.length > 0) show(button.title);
        return;
      }
      if (monitorStreamBusy.has(deviceId)) {
        monitorStreamProgress.set(deviceId, { state: "running", detail: mode === "start" ? "\u6B63\u5728\u542F\u52A8\u56FE\u4F20" : "\u6B63\u5728\u505C\u6B62\u56FE\u4F20" });
        monitorRailDeviceId = deviceId;
        monitorRailKind = "stream";
        show(`${callSign(deviceId)}\uFF1A${mode === "start" ? "\u6B63\u5728\u542F\u52A8\u56FE\u4F20" : "\u6B63\u5728\u505C\u6B62\u56FE\u4F20"}`);
        try {
          await render();
        } catch {
        }
        return;
      }
      const streamReservation = actionRequests.reserve(deviceId, mode === "start" ? "stream-start" : "stream-stop");
      if (!streamReservation.ok) {
        show(streamReservation.reason);
        return;
      }
      renderActionRequests(view);
      const clickReminder = mode === "start" ? FlightControllerVideoHint.evaluate({
        hasConnectedOnce: read4(connectionOf(device), "flightControllerHasConnectedOnce"),
        streaming: read4(read4(connectionOf(device), "live"), "streaming"),
        fps: read4(read4(connectionOf(device), "live"), "fps")
      }).banner : null;
      if (clickReminder === null) monitorFlightControllerHints.delete(deviceId);
      else monitorFlightControllerHints.set(deviceId, clickReminder);
      const runningDetail = mode === "start" ? "\u6B63\u5728\u542F\u52A8\u56FE\u4F20" : "\u6B63\u5728\u505C\u6B62\u56FE\u4F20";
      monitorStreamBusy.add(deviceId);
      monitorStreamProgress.set(deviceId, { state: "running", detail: runningDetail });
      monitorRailDeviceId = deviceId;
      monitorRailKind = "stream";
      show(`${callSign(deviceId)}\uFF1A${mode === "start" ? "\u6B63\u5728\u542F\u52A8\u56FE\u4F20" : "\u6B63\u5728\u505C\u6B62\u56FE\u4F20"}`);
      try {
        await render();
      } catch {
      }
      try {
        if (mode === "stop") {
          for (const [index, lane] of monitorPlayers) {
            if (lane.deviceId === deviceId) releaseMonitorSlot(index);
          }
        }
        const result = await bridge().invoke(mode === "start" ? "stream-start" : "stream-stop", { deviceId });
        const feedback = captureFeedback(mode === "start" ? "stream-start" : "stream-stop", deviceId, feedbackDeviceEpoch(view, deviceId), result);
        let latest = device;
        try {
          const refreshed = monitorDevices(projectCached())[slot];
          if (refreshed !== void 0) latest = refreshed;
        } catch {
        }
        const connection = connectionOf(latest);
        const live = read4(connection, "live");
        const reminder = mode === "start" ? FlightControllerVideoHint.evaluate({
          hasConnectedOnce: read4(connection, "flightControllerHasConnectedOnce"),
          streaming: read4(live, "streaming"),
          fps: read4(live, "fps")
        }).banner : null;
        if (mode === "stop" || reminder === null) monitorFlightControllerHints.delete(deviceId);
        else monitorFlightControllerHints.set(deviceId, reminder);
        const done = feedback.outcome === "accepted" || feedback.outcome === "completed";
        if (mode === "stop") monitorStreamHoldCleared.add(deviceId);
        if (mode === "start" && !done) monitorStreamHoldCleared.delete(deviceId);
        monitorStreamProgress.set(deviceId, { state: done ? "done" : "failed", detail: feedback.message });
        show(`${callSign(deviceId)}\uFF1A${feedback.message}`);
      } catch {
        monitorStreamProgress.set(deviceId, { state: "failed", detail: "\u6682\u65F6\u65E0\u6CD5\u5B8C\u6210\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5" });
        show(`${callSign(deviceId)}\uFF1A\u6682\u65F6\u65E0\u6CD5\u5B8C\u6210\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5`);
      } finally {
        streamReservation.release();
        monitorStreamBusy.delete(deviceId);
        try {
          await render();
        } catch {
        }
      }
    });
  });
  document.querySelectorAll("[data-action]").forEach((button) => {
    button.addEventListener("click", async () => {
      const action = button.dataset.action ?? "";
      let view;
      try {
        view = projectCached();
      } catch {
        show("\u754C\u9762\u8BFB\u53D6\u5931\u8D25\uFF0C\u672C\u6B21\u64CD\u4F5C\u672A\u53D1\u9001\uFF1B\u8BF7\u786E\u8BA4\u624B\u673A\u4ECD\u8FDE\u63A5\u540E\u91CD\u8BD5");
        renderFlightConfirmationFallback();
        renderMissionStartConfirmationFallback();
        return;
      }
      const requestDeviceId = action.startsWith("stream-") || action.startsWith("photo-") ? view.streamDeviceId : view.missionDeviceId;
      const reservation = actionRequests.reserve(requestDeviceId, action);
      if (!reservation.ok) {
        show(reservation.reason);
        return;
      }
      renderActionRequests(view);
      try {
        const armDecision = interpretArmClick(armedCommand, action, Date.now());
        if (isArmableAction(action)) {
          if (armDecision.kind === "ignore") return;
          if (armDecision.kind === "confirm") {
            const dispatch = flightConfirmDispatch(pendingFlightConfirmation, {
              deviceId: view.missionDeviceId,
              uiAction: action,
              nowMs: Date.now()
            });
            if (dispatch.kind === "wait") return;
            armedCommand = null;
            clearArmTimer();
            await run("flight-confirm", "flight-confirm", { deviceId: dispatch.confirmation.deviceId, confirmationId: dispatch.confirmation.confirmationId }, void 0, action);
            return;
          }
          if (armDecision.kind !== "arm") return;
          if (armedCommand !== null && armedCommand.action !== action) await cancelArmedBackend();
          armedCommand = armDecision.next;
          await run(action, "flight-request", { deviceId: view.missionDeviceId, action: action.replace("flight-", "") }, view);
          if (!confirmationMatchesClick(pendingFlightConfirmation, { deviceId: view.missionDeviceId, uiAction: action, nowMs: Date.now() })) {
            armedCommand = null;
            clearArmTimer();
          } else scheduleArmExpiry();
          return;
        }
        if (armedCommand !== null) {
          await cancelArmedBackend();
          armedCommand = null;
          clearArmTimer();
        }
        const streamAction = action.startsWith("stream-") || action.startsWith("photo-");
        const deviceId = streamAction ? view.streamDeviceId : view.missionDeviceId;
        if (action === "stream-select") {
          await run(action, "stream-select", { deviceId }, view);
          show("\u56FE\u4F20\u5DF2\u9009\u4E2D\uFF0C\u7B49\u5F85\u672C\u9875\u51FA\u753B");
          return;
        }
        if (action === "stream-stop") detachVideo();
        const names = {
          "mission-stage": "mission-stage",
          "mission-upload": "mission-upload",
          "mission-start": "mission-start",
          "mission-pause": "mission-pause",
          "mission-resume": "mission-resume",
          "mission-stop": "mission-stop",
          "stream-start": "stream-start",
          "stream-stop": "stream-stop",
          "photo-capture": "photo-capture",
          "photo-fetch": "photo-fetch",
          "flight-confirm-landing": "flight-request",
          "flight-stop-takeoff": "flight-request",
          "flight-stop-auto-landing": "flight-request"
        };
        const invokeName = names[action];
        if (invokeName === void 0) return;
        const input = invokeName === "flight-request" ? { deviceId, action: action.replace("flight-", "") } : { deviceId };
        if (action === "mission-stage" && view.selectedRoute !== null && deviceId !== null) {
          const assigned = unwrap2(await bridge().invoke("assignment-assign", { deviceId, routeId: view.selectedRoute.routeId }));
          if (!accepted(assigned)) {
            blocked("assignment-assign", "\u822A\u7EBF\u672A\u80FD\u8D4B\u7ED9\u6240\u9009\u624B\u673A\uFF0C\u672A\u5F00\u59CB\u4F20\u8F93", deviceId, feedbackDeviceEpoch(view, deviceId));
            return;
          }
        }
        const previousConfirmationId = pendingFlightConfirmation?.confirmationId ?? null;
        await run(action, invokeName, input, view);
        if (isSameClickFlightConfirm(action)) {
          const dispatch = sameClickConfirmDispatch(pendingFlightConfirmation, previousConfirmationId, {
            deviceId,
            uiAction: action,
            nowMs: Date.now()
          });
          if (dispatch.kind === "dispatch") {
            await run("flight-confirm", "flight-confirm", { deviceId: dispatch.confirmation.deviceId, confirmationId: dispatch.confirmation.confirmationId }, void 0, action);
          }
        }
      } catch {
        show("\u64CD\u4F5C\u7ED3\u679C\u5C1A\u672A\u786E\u8BA4\uFF0C\u8BF7\u67E5\u770B\u8BBE\u5907\u72B6\u6001\u540E\u518D\u64CD\u4F5C");
      } finally {
        reservation.release();
        void snapshotRefresh.request();
        try {
          await render();
        } catch {
        }
      }
    });
  });
  void snapshotRefresh.request();
})();
/*! Bundled license information:

mpegts.js/dist/mpegts.js:
  (*! For license information please see mpegts.js.LICENSE.txt *)
*/

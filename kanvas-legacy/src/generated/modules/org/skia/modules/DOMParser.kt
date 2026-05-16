package org.skia.modules

import kotlin.Boolean
import kotlin.Char
import kotlin.Float
import kotlin.Int
import kotlin.String
import kotlin.ULong
import org.skia.memory.SkArenaAlloc
import undefined.MatchFunc

/**
 * C++ original:
 * ```cpp
 * class DOMParser {
 * public:
 *     explicit DOMParser(SkArenaAlloc& alloc) : fAlloc(alloc) {
 *         fValueStack.reserve(kValueStackReserve);
 *         fUnescapeBuffer.reserve(kUnescapeBufferReserve);
 *     }
 *
 *     Value parse(const char* p, size_t size) {
 *         if (!size) {
 *             return this->error(NullValue(), p, "invalid empty input");
 *         }
 *
 *         const char* p_stop = p + size - 1;
 *
 *         // We're only checking for end-of-stream on object/array close('}',']'),
 *         // so we must trim any whitespace from the buffer tail.
 *         while (p_stop > p && is_ws(*p_stop)) --p_stop;
 *
 *         SkASSERT(p_stop >= p && p_stop < p + size);
 *         if (!is_eoscope(*p_stop)) {
 *             return this->error(NullValue(), p_stop, "invalid top-level value");
 *         }
 *
 *         p = skip_ws(p);
 *
 *         switch (*p) {
 *             case '{':
 *                 goto match_object;
 *             case '[':
 *                 goto match_array;
 *             default:
 *                 return this->error(NullValue(), p, "invalid top-level value");
 *         }
 *
 *     match_object:
 *         SkASSERT(*p == '{');
 *         p = skip_ws(p + 1);
 *
 *         this->pushObjectScope();
 *
 *         if (*p == '}') goto pop_object;
 *
 *         // goto match_object_key;
 *     match_object_key:
 *         p = skip_ws(p);
 *         if (*p != '"') return this->error(NullValue(), p, "expected object key");
 *
 *         p = this->matchString(p, p_stop, [this](const char* key, size_t size, const char* eos) {
 *             this->pushObjectKey(key, size, eos);
 *         });
 *         if (!p) return NullValue();
 *
 *         p = skip_ws(p);
 *         if (*p != ':') return this->error(NullValue(), p, "expected ':' separator");
 *
 *         ++p;
 *
 *         // goto match_value;
 *     match_value:
 *         p = skip_ws(p);
 *
 *         switch (*p) {
 *             case '\0':
 *                 return this->error(NullValue(), p, "unexpected input end");
 *             case '"':
 *                 p = this->matchString(
 *                         p, p_stop, [this](const char* str, size_t size, const char* eos) {
 *                             this->pushString(str, size, eos);
 *                         });
 *                 break;
 *             case '[':
 *                 goto match_array;
 *             case 'f':
 *                 p = this->matchFalse(p);
 *                 break;
 *             case 'n':
 *                 p = this->matchNull(p);
 *                 break;
 *             case 't':
 *                 p = this->matchTrue(p);
 *                 break;
 *             case '{':
 *                 goto match_object;
 *             default:
 *                 p = this->matchNumber(p);
 *                 break;
 *         }
 *
 *         if (!p) return NullValue();
 *
 *         // goto match_post_value;
 *     match_post_value:
 *         SkASSERT(!this->inTopLevelScope());
 *
 *         p = skip_ws(p);
 *         switch (*p) {
 *             case ',':
 *                 ++p;
 *                 if (this->inObjectScope()) {
 *                     goto match_object_key;
 *                 } else {
 *                     SkASSERT(this->inArrayScope());
 *                     goto match_value;
 *                 }
 *             case ']':
 *                 goto pop_array;
 *             case '}':
 *                 goto pop_object;
 *             default:
 *                 return this->error(NullValue(), p - 1, "unexpected value-trailing token");
 *         }
 *
 *         // unreachable
 *         SkASSERT(false);
 *
 *     pop_object:
 *         SkASSERT(*p == '}');
 *
 *         if (this->inArrayScope()) {
 *             return this->error(NullValue(), p, "unexpected object terminator");
 *         }
 *
 *         this->popObjectScope();
 *
 *         // goto pop_common
 *     pop_common:
 *         SkASSERT(is_eoscope(*p));
 *
 *         if (this->inTopLevelScope()) {
 *             SkASSERT(fValueStack.size() == 1);
 *
 *             // Success condition: parsed the top level element and reached the stop token.
 *             return p == p_stop ? fValueStack.front()
 *                                : this->error(NullValue(), p + 1, "trailing root garbage");
 *         }
 *
 *         if (p == p_stop) {
 *             return this->error(NullValue(), p, "unexpected end-of-input");
 *         }
 *
 *         ++p;
 *
 *         goto match_post_value;
 *
 *     match_array:
 *         SkASSERT(*p == '[');
 *         p = skip_ws(p + 1);
 *
 *         this->pushArrayScope();
 *
 *         if (*p != ']') goto match_value;
 *
 *         // goto pop_array;
 *     pop_array:
 *         SkASSERT(*p == ']');
 *
 *         if (this->inObjectScope()) {
 *             return this->error(NullValue(), p, "unexpected array terminator");
 *         }
 *
 *         this->popArrayScope();
 *
 *         goto pop_common;
 *
 *         SkASSERT(false);
 *         return NullValue();
 *     }
 *
 *     std::tuple<const char*, const SkString> getError() const {
 *         return std::make_tuple(fErrorToken, fErrorMessage);
 *     }
 *
 * private:
 *     SkArenaAlloc&         fAlloc;
 *
 *     // Pending values stack.
 *     inline static constexpr size_t kValueStackReserve = 256;
 *     std::vector<Value>    fValueStack;
 *
 *     // String unescape buffer.
 *     inline static constexpr size_t kUnescapeBufferReserve = 512;
 *     std::vector<char>     fUnescapeBuffer;
 *
 *     // Tracks the current object/array scope, as an index into fStack:
 *     //
 *     //   - for objects: fScopeIndex =  (index of first value in scope)
 *     //   - for arrays : fScopeIndex = -(index of first value in scope)
 *     //
 *     // fScopeIndex == 0 IFF we are at the top level (no current/active scope).
 *     intptr_t              fScopeIndex = 0;
 *
 *     // Error reporting.
 *     const char*           fErrorToken = nullptr;
 *     SkString              fErrorMessage;
 *
 *     bool inTopLevelScope() const { return fScopeIndex == 0; }
 *     bool inObjectScope()   const { return fScopeIndex >  0; }
 *     bool inArrayScope()    const { return fScopeIndex <  0; }
 *
 *     // Helper for masquerading raw primitive types as Values (bypassing tagging, etc).
 *     template <typename T>
 *     class RawValue final : public Value {
 *     public:
 *         explicit RawValue(T v) {
 *             static_assert(sizeof(T) <= sizeof(Value), "");
 *             *this->cast<T>() = v;
 *         }
 *
 *         T operator*() const { return *this->cast<T>(); }
 *     };
 *
 *     template <typename VectorT>
 *     void popScopeAsVec(size_t scope_start) {
 *         SkASSERT(scope_start > 0);
 *         SkASSERT(scope_start <= fValueStack.size());
 *
 *         using T = typename VectorT::ValueT;
 *         static_assert( sizeof(T) >=  sizeof(Value), "");
 *         static_assert( sizeof(T)  %  sizeof(Value) == 0, "");
 *         static_assert(alignof(T) == alignof(Value), "");
 *
 *         const auto scope_count = fValueStack.size() - scope_start,
 *                    count = scope_count / (sizeof(T) / sizeof(Value));
 *         SkASSERT(scope_count % (sizeof(T) / sizeof(Value)) == 0);
 *
 *         const auto* begin = reinterpret_cast<const T*>(fValueStack.data() + scope_start);
 *
 *         // Restore the previous scope index from saved placeholder value,
 *         // and instantiate as a vector of values in scope.
 *         auto& placeholder = fValueStack[scope_start - 1];
 *         fScopeIndex = *static_cast<RawValue<intptr_t>&>(placeholder);
 *         placeholder = VectorT(begin, count, fAlloc);
 *
 *         // Drop the (consumed) values in scope.
 *         fValueStack.resize(scope_start);
 *     }
 *
 *     void pushObjectScope() {
 *         // Save a scope index now, and then later we'll overwrite this value as the Object itself.
 *         fValueStack.push_back(RawValue<intptr_t>(fScopeIndex));
 *
 *         // New object scope.
 *         fScopeIndex = SkTo<intptr_t>(fValueStack.size());
 *     }
 *
 *     void popObjectScope() {
 *         SkASSERT(this->inObjectScope());
 *         this->popScopeAsVec<ObjectValue>(SkTo<size_t>(fScopeIndex));
 *
 *         SkDEBUGCODE(
 *             const auto& obj = fValueStack.back().as<ObjectValue>();
 *             SkASSERT(obj.is<ObjectValue>());
 *             for (const auto& member : obj) {
 *                 SkASSERT(member.fKey.is<StringValue>());
 *             }
 *         )
 *     }
 *
 *     void pushArrayScope() {
 *         // Save a scope index now, and then later we'll overwrite this value as the Array itself.
 *         fValueStack.push_back(RawValue<intptr_t>(fScopeIndex));
 *
 *         // New array scope.
 *         fScopeIndex = -SkTo<intptr_t>(fValueStack.size());
 *     }
 *
 *     void popArrayScope() {
 *         SkASSERT(this->inArrayScope());
 *         this->popScopeAsVec<ArrayValue>(SkTo<size_t>(-fScopeIndex));
 *
 *         SkDEBUGCODE(
 *             const auto& arr = fValueStack.back().as<ArrayValue>();
 *             SkASSERT(arr.is<ArrayValue>());
 *         )
 *     }
 *
 *     void pushObjectKey(const char* key, size_t size, const char* eos) {
 *         SkASSERT(this->inObjectScope());
 *         SkASSERT(fValueStack.size() >= SkTo<size_t>(fScopeIndex));
 *         SkASSERT(!((fValueStack.size() - SkTo<size_t>(fScopeIndex)) & 1));
 *         this->pushString(key, size, eos);
 *     }
 *
 *     void pushTrue() { fValueStack.push_back(BoolValue(true)); }
 *
 *     void pushFalse() { fValueStack.push_back(BoolValue(false)); }
 *
 *     void pushNull() { fValueStack.push_back(NullValue()); }
 *
 *     void pushString(const char* s, size_t size, const char* eos) {
 *         fValueStack.push_back(FastString(s, size, eos, fAlloc));
 *     }
 *
 *     void pushInt32(int32_t i) { fValueStack.push_back(NumberValue(i)); }
 *
 *     void pushFloat(float f) { fValueStack.push_back(NumberValue(f)); }
 *
 *     template <typename T>
 *     T error(T&& ret_val, const char* p, const char* msg) {
 * #if defined(SK_JSON_REPORT_ERRORS)
 *         fErrorToken = p;
 *         fErrorMessage.set(msg);
 * #endif
 *         return ret_val;
 *     }
 *
 *     const char* matchTrue(const char* p) {
 *         SkASSERT(p[0] == 't');
 *
 *         if (p[1] == 'r' && p[2] == 'u' && p[3] == 'e') {
 *             this->pushTrue();
 *             return p + 4;
 *         }
 *
 *         return this->error(nullptr, p, "invalid token");
 *     }
 *
 *     const char* matchFalse(const char* p) {
 *         SkASSERT(p[0] == 'f');
 *
 *         if (p[1] == 'a' && p[2] == 'l' && p[3] == 's' && p[4] == 'e') {
 *             this->pushFalse();
 *             return p + 5;
 *         }
 *
 *         return this->error(nullptr, p, "invalid token");
 *     }
 *
 *     const char* matchNull(const char* p) {
 *         SkASSERT(p[0] == 'n');
 *
 *         if (p[1] == 'u' && p[2] == 'l' && p[3] == 'l') {
 *             this->pushNull();
 *             return p + 4;
 *         }
 *
 *         return this->error(nullptr, p, "invalid token");
 *     }
 *
 *     const std::vector<char>* unescapeString(const char* begin, const char* end) {
 *         fUnescapeBuffer.clear();
 *
 *         for (const auto* p = begin; p != end; ++p) {
 *             if (*p != '\\') {
 *                 fUnescapeBuffer.push_back(*p);
 *                 continue;
 *             }
 *
 *             if (++p == end) {
 *                 return nullptr;
 *             }
 *
 *             switch (*p) {
 *             case  '"': fUnescapeBuffer.push_back( '"'); break;
 *             case '\\': fUnescapeBuffer.push_back('\\'); break;
 *             case  '/': fUnescapeBuffer.push_back( '/'); break;
 *             case  'b': fUnescapeBuffer.push_back('\b'); break;
 *             case  'f': fUnescapeBuffer.push_back('\f'); break;
 *             case  'n': fUnescapeBuffer.push_back('\n'); break;
 *             case  'r': fUnescapeBuffer.push_back('\r'); break;
 *             case  't': fUnescapeBuffer.push_back('\t'); break;
 *             case  'u': {
 *                 if (p + 4 >= end) {
 *                     return nullptr;
 *                 }
 *
 *                     uint32_t hexed;
 *                     const char hex_str[] = {p[1], p[2], p[3], p[4], '\0'};
 *                     const auto* eos = SkParse::FindHex(hex_str, &hexed);
 *                     if (!eos || *eos) {
 *                         return nullptr;
 *                     }
 *
 *                     char utf8[SkUTF::kMaxBytesInUTF8Sequence];
 *                     const auto utf8_len = SkUTF::ToUTF8(SkTo<SkUnichar>(hexed), utf8);
 *                     fUnescapeBuffer.insert(fUnescapeBuffer.end(), utf8, utf8 + utf8_len);
 *                     p += 4;
 *                 } break;
 *                 default:
 *                     return nullptr;
 *             }
 *         }
 *
 *         return &fUnescapeBuffer;
 *     }
 *
 *     template <typename MatchFunc>
 *     const char* matchString(const char* p, const char* p_stop, MatchFunc&& func) {
 *         SkASSERT(*p == '"');
 *         const auto* s_begin = p + 1;
 *         bool requires_unescape = false;
 *
 *         do {
 *             // Consume string chars.
 *             // This is the fast path, and hopefully we only hit it once then quick-exit below.
 *             for (p = p + 1; !is_eostring(*p); ++p);
 *
 *             if (*p == '"') {
 *                 // Valid string found.
 *                 if (!requires_unescape) {
 *                     func(s_begin, p - s_begin, p_stop);
 *                 } else {
 *                     // Slow unescape.  We could avoid this extra copy with some effort,
 *                     // but in practice escaped strings should be rare.
 *                     const auto* buf = this->unescapeString(s_begin, p);
 *                     if (!buf) {
 *                         break;
 *                     }
 *
 *                     SkASSERT(!buf->empty());
 *                     func(buf->data(), buf->size(), buf->data() + buf->size() - 1);
 *                 }
 *                 return p + 1;
 *             }
 *
 *             if (*p == '\\') {
 *                 requires_unescape = true;
 *                 ++p;
 *                 continue;
 *             }
 *
 *             // End-of-scope chars are special: we use them to tag the end of the input.
 *             // Thus they cannot be consumed indiscriminately -- we need to check if we hit the
 *             // end of the input.  To that effect, we treat them as string terminators above,
 *             // then we catch them here.
 *             if (is_eoscope(*p)) {
 *                 continue;
 *             }
 *
 *             // Invalid/unexpected char.
 *             break;
 *         } while (p != p_stop);
 *
 *         // Premature end-of-input, or illegal string char.
 *         return this->error(nullptr, s_begin - 1, "invalid string");
 *     }
 *
 *     const char* matchFastFloatDecimalPart(const char* p, int sign, float f, int exp) {
 *         SkASSERT(exp <= 0);
 *
 *         for (;;) {
 *             if (!is_digit(*p)) break;
 *             f = f * 10.f + (*p++ - '0'); --exp;
 *             if (!is_digit(*p)) break;
 *             f = f * 10.f + (*p++ - '0'); --exp;
 *         }
 *
 *         const auto decimal_scale = pow10(exp);
 *         if (is_numeric(*p) || !decimal_scale) {
 *             SkASSERT((*p == '.' || *p == 'e' || *p == 'E') || !decimal_scale);
 *             // Malformed input, or an (unsupported) exponent, or a collapsed decimal factor.
 *             return nullptr;
 *         }
 *
 *         this->pushFloat(sign * f * decimal_scale);
 *
 *         return p;
 *     }
 *
 *     const char* matchFastFloatPart(const char* p, int sign, float f) {
 *         for (;;) {
 *             if (!is_digit(*p)) break;
 *             f = f * 10.f + (*p++ - '0');
 *             if (!is_digit(*p)) break;
 *             f = f * 10.f + (*p++ - '0');
 *         }
 *
 *         if (!is_numeric(*p)) {
 *             // Matched (integral) float.
 *             this->pushFloat(sign * f);
 *             return p;
 *         }
 *
 *         return (*p == '.') ? this->matchFastFloatDecimalPart(p + 1, sign, f, 0)
 *                            : nullptr;
 *     }
 *
 *     const char* matchFast32OrFloat(const char* p) {
 *         int sign = 1;
 *         if (*p == '-') {
 *             sign = -1;
 *             ++p;
 *         }
 *
 *         const auto* digits_start = p;
 *
 *         int32_t n32 = 0;
 *
 *         // This is the largest absolute int32 value we can handle before
 *         // risking overflow *on the next digit* (214748363).
 *         static constexpr int32_t kMaxInt32 = (std::numeric_limits<int32_t>::max() - 9) / 10;
 *
 *         if (is_digit(*p)) {
 *             n32 = (*p++ - '0');
 *             for (;;) {
 *                 if (!is_digit(*p) || n32 > kMaxInt32) break;
 *                 n32 = n32 * 10 + (*p++ - '0');
 *             }
 *         }
 *
 *         if (!is_numeric(*p)) {
 *             // Did we actually match any digits?
 *             if (p > digits_start) {
 *                 this->pushInt32(sign * n32);
 *                 return p;
 *             }
 *             return nullptr;
 *         }
 *
 *         if (*p == '.') {
 *             const auto* decimals_start = ++p;
 *
 *             int exp = 0;
 *
 *             for (;;) {
 *                 if (!is_digit(*p) || n32 > kMaxInt32) break;
 *                 n32 = n32 * 10 + (*p++ - '0'); --exp;
 *                 if (!is_digit(*p) || n32 > kMaxInt32) break;
 *                 n32 = n32 * 10 + (*p++ - '0'); --exp;
 *             }
 *
 *             if (!is_numeric(*p)) {
 *                 // Did we actually match any digits?
 *                 if (p > decimals_start) {
 *                     this->pushFloat(sign * n32 * pow10(exp));
 *                     return p;
 *                 }
 *                 return nullptr;
 *             }
 *
 *             if (n32 > kMaxInt32) {
 *                 // we ran out on n32 bits
 *                 return this->matchFastFloatDecimalPart(p, sign, n32, exp);
 *             }
 *         }
 *
 *         return this->matchFastFloatPart(p, sign, n32);
 *     }
 *
 *     const char* matchNumber(const char* p) {
 *         if (const auto* fast = this->matchFast32OrFloat(p)) return fast;
 *
 *         // slow fallback
 *         char* matched;
 *         float f = strtof(p, &matched);
 *         if (matched > p) {
 *             this->pushFloat(f);
 *             return matched;
 *         }
 *         return this->error(nullptr, p, "invalid numeric token");
 *     }
 * }
 * ```
 */
public data class DOMParser public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkArenaAlloc&         fAlloc
   * ```
   */
  private var fAlloc: SkArenaAlloc,
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr size_t kValueStackReserve = 256
   * ```
   */
  private var fValueStack: Int,
  /**
   * C++ original:
   * ```cpp
   * std::vector<Value>    fValueStack
   * ```
   */
  private var fUnescapeBuffer: Int,
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr size_t kUnescapeBufferReserve = 512
   * ```
   */
  private var fScopeIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * std::vector<char>     fUnescapeBuffer
   * ```
   */
  private val fErrorToken: String?,
  /**
   * C++ original:
   * ```cpp
   * intptr_t              fScopeIndex
   * ```
   */
  private var fErrorMessage: String,
) {
  /**
   * C++ original:
   * ```cpp
   * Value parse(const char* p, size_t size) {
   *         if (!size) {
   *             return this->error(NullValue(), p, "invalid empty input");
   *         }
   *
   *         const char* p_stop = p + size - 1;
   *
   *         // We're only checking for end-of-stream on object/array close('}',']'),
   *         // so we must trim any whitespace from the buffer tail.
   *         while (p_stop > p && is_ws(*p_stop)) --p_stop;
   *
   *         SkASSERT(p_stop >= p && p_stop < p + size);
   *         if (!is_eoscope(*p_stop)) {
   *             return this->error(NullValue(), p_stop, "invalid top-level value");
   *         }
   *
   *         p = skip_ws(p);
   *
   *         switch (*p) {
   *             case '{':
   *                 goto match_object;
   *             case '[':
   *                 goto match_array;
   *             default:
   *                 return this->error(NullValue(), p, "invalid top-level value");
   *         }
   *
   *     match_object:
   *         SkASSERT(*p == '{');
   *         p = skip_ws(p + 1);
   *
   *         this->pushObjectScope();
   *
   *         if (*p == '}') goto pop_object;
   *
   *         // goto match_object_key;
   *     match_object_key:
   *         p = skip_ws(p);
   *         if (*p != '"') return this->error(NullValue(), p, "expected object key");
   *
   *         p = this->matchString(p, p_stop, [this](const char* key, size_t size, const char* eos) {
   *             this->pushObjectKey(key, size, eos);
   *         });
   *         if (!p) return NullValue();
   *
   *         p = skip_ws(p);
   *         if (*p != ':') return this->error(NullValue(), p, "expected ':' separator");
   *
   *         ++p;
   *
   *         // goto match_value;
   *     match_value:
   *         p = skip_ws(p);
   *
   *         switch (*p) {
   *             case '\0':
   *                 return this->error(NullValue(), p, "unexpected input end");
   *             case '"':
   *                 p = this->matchString(
   *                         p, p_stop, [this](const char* str, size_t size, const char* eos) {
   *                             this->pushString(str, size, eos);
   *                         });
   *                 break;
   *             case '[':
   *                 goto match_array;
   *             case 'f':
   *                 p = this->matchFalse(p);
   *                 break;
   *             case 'n':
   *                 p = this->matchNull(p);
   *                 break;
   *             case 't':
   *                 p = this->matchTrue(p);
   *                 break;
   *             case '{':
   *                 goto match_object;
   *             default:
   *                 p = this->matchNumber(p);
   *                 break;
   *         }
   *
   *         if (!p) return NullValue();
   *
   *         // goto match_post_value;
   *     match_post_value:
   *         SkASSERT(!this->inTopLevelScope());
   *
   *         p = skip_ws(p);
   *         switch (*p) {
   *             case ',':
   *                 ++p;
   *                 if (this->inObjectScope()) {
   *                     goto match_object_key;
   *                 } else {
   *                     SkASSERT(this->inArrayScope());
   *                     goto match_value;
   *                 }
   *             case ']':
   *                 goto pop_array;
   *             case '}':
   *                 goto pop_object;
   *             default:
   *                 return this->error(NullValue(), p - 1, "unexpected value-trailing token");
   *         }
   *
   *         // unreachable
   *         SkASSERT(false);
   *
   *     pop_object:
   *         SkASSERT(*p == '}');
   *
   *         if (this->inArrayScope()) {
   *             return this->error(NullValue(), p, "unexpected object terminator");
   *         }
   *
   *         this->popObjectScope();
   *
   *         // goto pop_common
   *     pop_common:
   *         SkASSERT(is_eoscope(*p));
   *
   *         if (this->inTopLevelScope()) {
   *             SkASSERT(fValueStack.size() == 1);
   *
   *             // Success condition: parsed the top level element and reached the stop token.
   *             return p == p_stop ? fValueStack.front()
   *                                : this->error(NullValue(), p + 1, "trailing root garbage");
   *         }
   *
   *         if (p == p_stop) {
   *             return this->error(NullValue(), p, "unexpected end-of-input");
   *         }
   *
   *         ++p;
   *
   *         goto match_post_value;
   *
   *     match_array:
   *         SkASSERT(*p == '[');
   *         p = skip_ws(p + 1);
   *
   *         this->pushArrayScope();
   *
   *         if (*p != ']') goto match_value;
   *
   *         // goto pop_array;
   *     pop_array:
   *         SkASSERT(*p == ']');
   *
   *         if (this->inObjectScope()) {
   *             return this->error(NullValue(), p, "unexpected array terminator");
   *         }
   *
   *         this->popArrayScope();
   *
   *         goto pop_common;
   *
   *         SkASSERT(false);
   *         return NullValue();
   *     }
   * ```
   */
  public fun parse(p: String?, size: ULong): Value {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<const char*, const SkString> getError() const {
   *         return std::make_tuple(fErrorToken, fErrorMessage);
   *     }
   * ```
   */
  public fun getError(): Int {
    TODO("Implement getError")
  }

  /**
   * C++ original:
   * ```cpp
   * bool inTopLevelScope() const { return fScopeIndex == 0; }
   * ```
   */
  private fun inTopLevelScope(): Boolean {
    TODO("Implement inTopLevelScope")
  }

  /**
   * C++ original:
   * ```cpp
   * bool inObjectScope()   const { return fScopeIndex >  0; }
   * ```
   */
  private fun inObjectScope(): Boolean {
    TODO("Implement inObjectScope")
  }

  /**
   * C++ original:
   * ```cpp
   * bool inArrayScope()    const { return fScopeIndex <  0; }
   * ```
   */
  private fun inArrayScope(): Boolean {
    TODO("Implement inArrayScope")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename VectorT>
   *     void popScopeAsVec(size_t scope_start) {
   *         SkASSERT(scope_start > 0);
   *         SkASSERT(scope_start <= fValueStack.size());
   *
   *         using T = typename VectorT::ValueT;
   *         static_assert( sizeof(T) >=  sizeof(Value), "");
   *         static_assert( sizeof(T)  %  sizeof(Value) == 0, "");
   *         static_assert(alignof(T) == alignof(Value), "");
   *
   *         const auto scope_count = fValueStack.size() - scope_start,
   *                    count = scope_count / (sizeof(T) / sizeof(Value));
   *         SkASSERT(scope_count % (sizeof(T) / sizeof(Value)) == 0);
   *
   *         const auto* begin = reinterpret_cast<const T*>(fValueStack.data() + scope_start);
   *
   *         // Restore the previous scope index from saved placeholder value,
   *         // and instantiate as a vector of values in scope.
   *         auto& placeholder = fValueStack[scope_start - 1];
   *         fScopeIndex = *static_cast<RawValue<intptr_t>&>(placeholder);
   *         placeholder = VectorT(begin, count, fAlloc);
   *
   *         // Drop the (consumed) values in scope.
   *         fValueStack.resize(scope_start);
   *     }
   * ```
   */
  public fun <VectorT> popScopeAsVec(scopeStart: ULong) {
    TODO("Implement popScopeAsVec")
  }

  /**
   * C++ original:
   * ```cpp
   * void pushObjectScope() {
   *         // Save a scope index now, and then later we'll overwrite this value as the Object itself.
   *         fValueStack.push_back(RawValue<intptr_t>(fScopeIndex));
   *
   *         // New object scope.
   *         fScopeIndex = SkTo<intptr_t>(fValueStack.size());
   *     }
   * ```
   */
  public fun pushObjectScope() {
    TODO("Implement pushObjectScope")
  }

  /**
   * C++ original:
   * ```cpp
   * void popObjectScope() {
   *         SkASSERT(this->inObjectScope());
   *         this->popScopeAsVec<ObjectValue>(SkTo<size_t>(fScopeIndex));
   *
   *         SkDEBUGCODE(
   *             const auto& obj = fValueStack.back().as<ObjectValue>();
   *             SkASSERT(obj.is<ObjectValue>());
   *             for (const auto& member : obj) {
   *                 SkASSERT(member.fKey.is<StringValue>());
   *             }
   *         )
   *     }
   * ```
   */
  public fun popObjectScope() {
    TODO("Implement popObjectScope")
  }

  /**
   * C++ original:
   * ```cpp
   * void pushArrayScope() {
   *         // Save a scope index now, and then later we'll overwrite this value as the Array itself.
   *         fValueStack.push_back(RawValue<intptr_t>(fScopeIndex));
   *
   *         // New array scope.
   *         fScopeIndex = -SkTo<intptr_t>(fValueStack.size());
   *     }
   * ```
   */
  public fun pushArrayScope() {
    TODO("Implement pushArrayScope")
  }

  /**
   * C++ original:
   * ```cpp
   * void popArrayScope() {
   *         SkASSERT(this->inArrayScope());
   *         this->popScopeAsVec<ArrayValue>(SkTo<size_t>(-fScopeIndex));
   *
   *         SkDEBUGCODE(
   *             const auto& arr = fValueStack.back().as<ArrayValue>();
   *             SkASSERT(arr.is<ArrayValue>());
   *         )
   *     }
   * ```
   */
  public fun popArrayScope() {
    TODO("Implement popArrayScope")
  }

  /**
   * C++ original:
   * ```cpp
   * void pushObjectKey(const char* key, size_t size, const char* eos) {
   *         SkASSERT(this->inObjectScope());
   *         SkASSERT(fValueStack.size() >= SkTo<size_t>(fScopeIndex));
   *         SkASSERT(!((fValueStack.size() - SkTo<size_t>(fScopeIndex)) & 1));
   *         this->pushString(key, size, eos);
   *     }
   * ```
   */
  public fun pushObjectKey(
    key: String?,
    size: ULong,
    eos: String?,
  ) {
    TODO("Implement pushObjectKey")
  }

  /**
   * C++ original:
   * ```cpp
   * void pushTrue() { fValueStack.push_back(BoolValue(true)); }
   * ```
   */
  public fun pushTrue() {
    TODO("Implement pushTrue")
  }

  /**
   * C++ original:
   * ```cpp
   * void pushFalse() { fValueStack.push_back(BoolValue(false)); }
   * ```
   */
  public fun pushFalse() {
    TODO("Implement pushFalse")
  }

  /**
   * C++ original:
   * ```cpp
   * void pushNull() { fValueStack.push_back(NullValue()); }
   * ```
   */
  public fun pushNull() {
    TODO("Implement pushNull")
  }

  /**
   * C++ original:
   * ```cpp
   * void pushString(const char* s, size_t size, const char* eos) {
   *         fValueStack.push_back(FastString(s, size, eos, fAlloc));
   *     }
   * ```
   */
  public fun pushString(
    s: String?,
    size: ULong,
    eos: String?,
  ) {
    TODO("Implement pushString")
  }

  /**
   * C++ original:
   * ```cpp
   * void pushInt32(int32_t i) { fValueStack.push_back(NumberValue(i)); }
   * ```
   */
  public fun pushInt32(i: Int) {
    TODO("Implement pushInt32")
  }

  /**
   * C++ original:
   * ```cpp
   * void pushFloat(float f) { fValueStack.push_back(NumberValue(f)); }
   * ```
   */
  public fun pushFloat(f: Float) {
    TODO("Implement pushFloat")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     T error(T&& ret_val, const char* p, const char* msg) {
   * #if defined(SK_JSON_REPORT_ERRORS)
   *         fErrorToken = p;
   *         fErrorMessage.set(msg);
   * #endif
   *         return ret_val;
   *     }
   * ```
   */
  public fun <T> error(
    retVal: T,
    p: String?,
    msg: String?,
  ): T {
    TODO("Implement error")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* matchTrue(const char* p) {
   *         SkASSERT(p[0] == 't');
   *
   *         if (p[1] == 'r' && p[2] == 'u' && p[3] == 'e') {
   *             this->pushTrue();
   *             return p + 4;
   *         }
   *
   *         return this->error(nullptr, p, "invalid token");
   *     }
   * ```
   */
  public fun matchTrue(p: String?): Char {
    TODO("Implement matchTrue")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* matchFalse(const char* p) {
   *         SkASSERT(p[0] == 'f');
   *
   *         if (p[1] == 'a' && p[2] == 'l' && p[3] == 's' && p[4] == 'e') {
   *             this->pushFalse();
   *             return p + 5;
   *         }
   *
   *         return this->error(nullptr, p, "invalid token");
   *     }
   * ```
   */
  public fun matchFalse(p: String?): Char {
    TODO("Implement matchFalse")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* matchNull(const char* p) {
   *         SkASSERT(p[0] == 'n');
   *
   *         if (p[1] == 'u' && p[2] == 'l' && p[3] == 'l') {
   *             this->pushNull();
   *             return p + 4;
   *         }
   *
   *         return this->error(nullptr, p, "invalid token");
   *     }
   * ```
   */
  public fun matchNull(p: String?): Char {
    TODO("Implement matchNull")
  }

  /**
   * C++ original:
   * ```cpp
   * const std::vector<char>* unescapeString(const char* begin, const char* end) {
   *         fUnescapeBuffer.clear();
   *
   *         for (const auto* p = begin; p != end; ++p) {
   *             if (*p != '\\') {
   *                 fUnescapeBuffer.push_back(*p);
   *                 continue;
   *             }
   *
   *             if (++p == end) {
   *                 return nullptr;
   *             }
   *
   *             switch (*p) {
   *             case  '"': fUnescapeBuffer.push_back( '"'); break;
   *             case '\\': fUnescapeBuffer.push_back('\\'); break;
   *             case  '/': fUnescapeBuffer.push_back( '/'); break;
   *             case  'b': fUnescapeBuffer.push_back('\b'); break;
   *             case  'f': fUnescapeBuffer.push_back('\f'); break;
   *             case  'n': fUnescapeBuffer.push_back('\n'); break;
   *             case  'r': fUnescapeBuffer.push_back('\r'); break;
   *             case  't': fUnescapeBuffer.push_back('\t'); break;
   *             case  'u': {
   *                 if (p + 4 >= end) {
   *                     return nullptr;
   *                 }
   *
   *                     uint32_t hexed;
   *                     const char hex_str[] = {p[1], p[2], p[3], p[4], '\0'};
   *                     const auto* eos = SkParse::FindHex(hex_str, &hexed);
   *                     if (!eos || *eos) {
   *                         return nullptr;
   *                     }
   *
   *                     char utf8[SkUTF::kMaxBytesInUTF8Sequence];
   *                     const auto utf8_len = SkUTF::ToUTF8(SkTo<SkUnichar>(hexed), utf8);
   *                     fUnescapeBuffer.insert(fUnescapeBuffer.end(), utf8, utf8 + utf8_len);
   *                     p += 4;
   *                 } break;
   *                 default:
   *                     return nullptr;
   *             }
   *         }
   *
   *         return &fUnescapeBuffer;
   *     }
   * ```
   */
  public fun unescapeString(begin: String?, end: String?): Int {
    TODO("Implement unescapeString")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename MatchFunc>
   *     const char* matchString(const char* p, const char* p_stop, MatchFunc&& func) {
   *         SkASSERT(*p == '"');
   *         const auto* s_begin = p + 1;
   *         bool requires_unescape = false;
   *
   *         do {
   *             // Consume string chars.
   *             // This is the fast path, and hopefully we only hit it once then quick-exit below.
   *             for (p = p + 1; !is_eostring(*p); ++p);
   *
   *             if (*p == '"') {
   *                 // Valid string found.
   *                 if (!requires_unescape) {
   *                     func(s_begin, p - s_begin, p_stop);
   *                 } else {
   *                     // Slow unescape.  We could avoid this extra copy with some effort,
   *                     // but in practice escaped strings should be rare.
   *                     const auto* buf = this->unescapeString(s_begin, p);
   *                     if (!buf) {
   *                         break;
   *                     }
   *
   *                     SkASSERT(!buf->empty());
   *                     func(buf->data(), buf->size(), buf->data() + buf->size() - 1);
   *                 }
   *                 return p + 1;
   *             }
   *
   *             if (*p == '\\') {
   *                 requires_unescape = true;
   *                 ++p;
   *                 continue;
   *             }
   *
   *             // End-of-scope chars are special: we use them to tag the end of the input.
   *             // Thus they cannot be consumed indiscriminately -- we need to check if we hit the
   *             // end of the input.  To that effect, we treat them as string terminators above,
   *             // then we catch them here.
   *             if (is_eoscope(*p)) {
   *                 continue;
   *             }
   *
   *             // Invalid/unexpected char.
   *             break;
   *         } while (p != p_stop);
   *
   *         // Premature end-of-input, or illegal string char.
   *         return this->error(nullptr, s_begin - 1, "invalid string");
   *     }
   * ```
   */
  public fun <MatchFunc> matchString(
    p: String?,
    pStop: String?,
    func: MatchFunc,
  ): Char {
    TODO("Implement matchString")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* matchFastFloatDecimalPart(const char* p, int sign, float f, int exp) {
   *         SkASSERT(exp <= 0);
   *
   *         for (;;) {
   *             if (!is_digit(*p)) break;
   *             f = f * 10.f + (*p++ - '0'); --exp;
   *             if (!is_digit(*p)) break;
   *             f = f * 10.f + (*p++ - '0'); --exp;
   *         }
   *
   *         const auto decimal_scale = pow10(exp);
   *         if (is_numeric(*p) || !decimal_scale) {
   *             SkASSERT((*p == '.' || *p == 'e' || *p == 'E') || !decimal_scale);
   *             // Malformed input, or an (unsupported) exponent, or a collapsed decimal factor.
   *             return nullptr;
   *         }
   *
   *         this->pushFloat(sign * f * decimal_scale);
   *
   *         return p;
   *     }
   * ```
   */
  public fun matchFastFloatDecimalPart(
    p: String?,
    sign: Int,
    f: Float,
    exp: Int,
  ): Char {
    TODO("Implement matchFastFloatDecimalPart")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* matchFastFloatPart(const char* p, int sign, float f) {
   *         for (;;) {
   *             if (!is_digit(*p)) break;
   *             f = f * 10.f + (*p++ - '0');
   *             if (!is_digit(*p)) break;
   *             f = f * 10.f + (*p++ - '0');
   *         }
   *
   *         if (!is_numeric(*p)) {
   *             // Matched (integral) float.
   *             this->pushFloat(sign * f);
   *             return p;
   *         }
   *
   *         return (*p == '.') ? this->matchFastFloatDecimalPart(p + 1, sign, f, 0)
   *                            : nullptr;
   *     }
   * ```
   */
  public fun matchFastFloatPart(
    p: String?,
    sign: Int,
    f: Float,
  ): Char {
    TODO("Implement matchFastFloatPart")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* matchFast32OrFloat(const char* p) {
   *         int sign = 1;
   *         if (*p == '-') {
   *             sign = -1;
   *             ++p;
   *         }
   *
   *         const auto* digits_start = p;
   *
   *         int32_t n32 = 0;
   *
   *         // This is the largest absolute int32 value we can handle before
   *         // risking overflow *on the next digit* (214748363).
   *         static constexpr int32_t kMaxInt32 = (std::numeric_limits<int32_t>::max() - 9) / 10;
   *
   *         if (is_digit(*p)) {
   *             n32 = (*p++ - '0');
   *             for (;;) {
   *                 if (!is_digit(*p) || n32 > kMaxInt32) break;
   *                 n32 = n32 * 10 + (*p++ - '0');
   *             }
   *         }
   *
   *         if (!is_numeric(*p)) {
   *             // Did we actually match any digits?
   *             if (p > digits_start) {
   *                 this->pushInt32(sign * n32);
   *                 return p;
   *             }
   *             return nullptr;
   *         }
   *
   *         if (*p == '.') {
   *             const auto* decimals_start = ++p;
   *
   *             int exp = 0;
   *
   *             for (;;) {
   *                 if (!is_digit(*p) || n32 > kMaxInt32) break;
   *                 n32 = n32 * 10 + (*p++ - '0'); --exp;
   *                 if (!is_digit(*p) || n32 > kMaxInt32) break;
   *                 n32 = n32 * 10 + (*p++ - '0'); --exp;
   *             }
   *
   *             if (!is_numeric(*p)) {
   *                 // Did we actually match any digits?
   *                 if (p > decimals_start) {
   *                     this->pushFloat(sign * n32 * pow10(exp));
   *                     return p;
   *                 }
   *                 return nullptr;
   *             }
   *
   *             if (n32 > kMaxInt32) {
   *                 // we ran out on n32 bits
   *                 return this->matchFastFloatDecimalPart(p, sign, n32, exp);
   *             }
   *         }
   *
   *         return this->matchFastFloatPart(p, sign, n32);
   *     }
   * ```
   */
  public fun matchFast32OrFloat(p: String?): Char {
    TODO("Implement matchFast32OrFloat")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* matchNumber(const char* p) {
   *         if (const auto* fast = this->matchFast32OrFloat(p)) return fast;
   *
   *         // slow fallback
   *         char* matched;
   *         float f = strtof(p, &matched);
   *         if (matched > p) {
   *             this->pushFloat(f);
   *             return matched;
   *         }
   *         return this->error(nullptr, p, "invalid numeric token");
   *     }
   * ```
   */
  public fun matchNumber(p: String?): Char {
    TODO("Implement matchNumber")
  }

  public open class RawValue<T> public constructor(
    v: T,
  ) : Value()

  public companion object {
    private val kValueStackReserve: Int = TODO("Initialize kValueStackReserve")

    private val kUnescapeBufferReserve: Int = TODO("Initialize kUnescapeBufferReserve")
  }
}

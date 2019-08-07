package kamon.stackdriver

import scala.annotation.tailrec

private[stackdriver] final class JsonStringBuilder(val underlying: java.lang.StringBuilder) extends AnyVal {

  def `{` : JsonStringBuilder = {
    underlying.append('{')
    this
  }

  def `}` : JsonStringBuilder = {
    underlying.append('}')
    this
  }

  def `:` : JsonStringBuilder = {
    underlying.append(':')
    this
  }

  def `,` : JsonStringBuilder = {
    underlying.append(',')
    this
  }

  def encodeNumber(l: scala.Long): JsonStringBuilder = {
    underlying.append(l)
    this
  }

  def encodeStringRaw(s: String): JsonStringBuilder = {
    underlying.append('"')
    underlying.append(s)
    underlying.append('"')
    this
  }

  //Taken from spray-json
  // $COVERAGE-OFF$
  def appendEncodedString(s: String): JsonStringBuilder = {
    @tailrec def firstToBeEncoded(ix: Int = 0): Int =
      if (ix == s.length) -1 else if (requiresEncoding(s.charAt(ix))) ix else firstToBeEncoded(ix + 1)

    def requiresEncoding(c: Char): Boolean =
      // from RFC 4627
      // unescaped = %x20-21 / %x23-5B / %x5D-10FFFF
      c match {
        case '"'  => true
        case '\\' => true
        case c    => c < 0x20
      }
    firstToBeEncoded() match {
      case -1 => underlying.append(s)
      case first =>
        underlying.append(s, 0, first)
        @tailrec def appendChar(ix: Int): Unit =
          if (ix < s.length) {
            s.charAt(ix) match {
              case c if !requiresEncoding(c) => underlying.append(c)
              case '"'                       => underlying.append("\\\"")
              case '\\'                      => underlying.append("\\\\")
              case '\b'                      => underlying.append("\\b")
              case '\f'                      => underlying.append("\\f")
              case '\n'                      => underlying.append("\\n")
              case '\r'                      => underlying.append("\\r")
              case '\t'                      => underlying.append("\\t")
              case x if x <= 0xF             => underlying.append("\\u000").append(Integer.toHexString(x))
              case x if x <= 0xFF            => underlying.append("\\u00").append(Integer.toHexString(x))
              case x if x <= 0xFFF           => underlying.append("\\u0").append(Integer.toHexString(x))
              case x                         => underlying.append("\\u").append(Integer.toHexString(x))
            }
            appendChar(ix + 1)
          }
        appendChar(first)
    }
    this
  }
  // $COVERAGE-ON$

  def startString(): JsonStringBuilder = {
    underlying.append('"')
    this
  }

  def endString(): JsonStringBuilder = {
    underlying.append('"')
    this
  }

  def appendNewline(): JsonStringBuilder = {
    underlying.append('\n')
    this
  }

  def encodeString(s: String): JsonStringBuilder =
    startString().appendEncodedString(s).endString()

  def result: String = underlying.toString
}

object JsonStringBuilder {

  val True  = "true"
  val False = "false"
  val Null  = "null"

  def getSingleThreaded: JsonStringBuilder = pool.get()

  private[this] final val initialBufferSize                  = 2 * 1024 // 2kB
  private[this] final val pool: ThreadLocalJsonStringBuilder = new ThreadLocalJsonStringBuilder(initialBufferSize)
  private[this] final class ThreadLocalJsonStringBuilder(initialBufferSize: Int) extends ThreadLocal[JsonStringBuilder] {
    override def initialValue(): JsonStringBuilder = new JsonStringBuilder(new java.lang.StringBuilder(initialBufferSize))
    override def get(): JsonStringBuilder = {
      var sb = super.get()
      if (sb.underlying.capacity > initialBufferSize) {
        sb = initialValue()
        set(sb)
      } else sb.underlying.setLength(0)
      sb
    }
  }
}

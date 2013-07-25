package ch.inf.vs.californium.coap;

import java.util.Arrays;

import ch.inf.vs.californium.resources.proxy.OptionNumberRegistry;

/**
 * Both requests and responses may include a list of one or more options. An
 * Option number is constructed with a bit mask to indicate if an option is
 * Critical/Elective, Unsafe/Safe and in the case of Safe, also a Cache-Key
 * indication.
 * 
 * <hr><blockquote><pre>
 *   0   1   2   3   4   5   6   7
 * +---+---+---+---+---+---+---+---+
 * |           | NoCacheKey| U | C |
 * +---+---+---+---+---+---+---+---+
 * </pre></blockquote><hr>
 * 
 * For a given option number {@code onum} we can compute
 * 
 * <hr><blockquote><pre>
 * Critical = (onum & 1);
 * UnSafe = (onum & 2);
 * NoCacheKey = ((onum & 0x1e) == 0x1c);
 * </pre></blockquote><hr>
 * 
 * CoAP defines several option numbers {@link CoAP.OptionRegistry}.
 */
public class Option implements Comparable<Option> {

	/** The option number. */
	private int number;
	
	/** The value as byte array. */
	private byte[] value; // not null
	
	/**
	 * Instantiates a new empty option.
	 */
	public Option() {
		this.value = new byte[0];
	}
	
	// Constructors
	
	/**
	 * Instantiates a new option with the specified option number.
	 *
	 * @param number the option number
	 */
	public Option(int number) {
		this.number = number;
		this.value = new byte[0];
	}
	
	/**
	 * Instantiates a new option with the specified option number and encodes
	 * the specified string as option value.
	 * 
	 * @param number the number
	 * @param str the option value as string
	 */
	public Option(int number, String str) {
		this.number = number;
		setStringValue(str);
	}
	
	/**
	 * Instantiates a new option with the specified option number and encodes
	 * the specified integer as option value.
	 *
	 * @param number the option number
	 * @param val the option value as integer
	 */
	public Option(int number, int val) {
		this.number = number;
		setIntegerValue(val);
	}
	
	/**
	 * Instantiates a new option with the specified option number and encodes
	 * the specified long as option value.
	 *
	 * @param number the option number
	 * @param val the option value as long
	 */
	public Option(int number, long val) {
		this.number = number;
		setLongValue(val);
	}
	
	/**
	 * Instantiates a new option with an arbitrary byte array as value.
	 *
	 * @param number the option number
	 * @param opaque the option value in bytes
	 */
	public Option(int number, byte[] opaque) {
		this.number = number;
		setValue(opaque);
	}
	
	// Getter and Setter
	
	/**
	 * Gets the length of the option value.
	 *
	 * @return the length
	 */
	public int getLength() {
		return value.length;
	}
	
	/**
	 * Gets the option number.
	 *
	 * @return the option number
	 */
	public int getNumber() {
		return number;
	}

	/**
	 * Sets the option number.
	 *
	 * @param number the new option number
	 */
	public void setNumber(int number) {
		this.number = number;
	}
	
	/**
	 * Gets the option value.
	 *
	 * @return the option value
	 */
	public byte[] getValue() {
		return value;
	}
	
	/**
	 * Gets the option value as string.
	 *
	 * @return the string value
	 */
	public String getStringValue() {
		return new String(value);
	}
	
	/**
	 * Gets the option value as integer.
	 *
	 * @return the integer value
	 */
	public int getIntegerValue() {
		int ret = 0;
		for (int i=0;i<value.length;i++) {
			ret += (value[value.length - i - 1] & 0xFF) << (i*8);
		}
		return ret;
	}
	
	/**
	 * Gets the option value as long.
	 *
	 * @return the long value
	 */
	public long getLongValue() {
		long ret = 0;
		for (int i=0;i<value.length;i++) {
			ret += (value[value.length - i - 1] & 0xFF) << (i*8);
		}
		return ret;
	}

	/**
	 * Sets the option value.
	 *
	 * @param value the new value
	 */
	public void setValue(byte[] value) {
		if (value == null)
			throw new NullPointerException();
		// TODO: Should we remove leading zeros?
		this.value = value;
	}
	
	/**
	 * Sets the option value from a string.
	 *
	 * @param str the new option value as string
	 */
	public void setStringValue(String str) {
		if (str == null)
			throw new NullPointerException();
		value = str.getBytes();
	}
	
	/**
	 * Sets the option value from an integer.
	 *
	 * @param val the new option value as integer
	 */
	public void setIntegerValue(int val) {
		int length = 0;
		for (int i=0;i<4;i++)
			if (val >= 1<<(i*8) || val < 0) length++;
			else break;
		value = new byte[length];
		for (int i=0;i<length;i++)
			value[i] = (byte) (val >> i*8);
	}
	
	/**
	 * Sets the option value from a long.
	 *
	 * @param val the new option value as long
	 */
	public void setLongValue(long val) {
		int length = 0;
		for (int i=0;i<8;i++)
			if (val >= 1L<<(i*8) || val < 0) length++;
			else break;
		value = new byte[length];
		for (int i=0;i<length;i++)
			value[i] = (byte) (val >> i*8);
	}
	
	/**
	 * Checks if is this option is critical.
	 *
	 * @return true, if is critical
	 */
	public boolean isCritical() {
		// Critical = (onum & 1);
		return (number & 1) != 0;
	}
	
	/**
	 * Checks if is this option is unsafe.
	 *
	 * @return true, if is unsafe
	 */
	public boolean isUnSafe() {
		// UnSafe = (onum & 2);
		return (number & 2) != 0;
	}
	
	/**
	 * Checks if this option is a NoCacheKey.
	 *
	 * @return true, if is NoCacheKey
	 */
	public boolean isNoCacheKey() {
		// NoCacheKey = ((onum & 0x1e) == 0x1c);
		return (number & 0x1E) == 0x1C;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(Option o) {
		return number - o.number;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof Option))
			return false;
		
		Option op = (Option) o;
		return number == op.number && Arrays.equals(value, op.value);
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return number*31 + value.hashCode();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "("+OptionNumberRegistry.toString(number)+":"+Arrays.toString(value)+")";
	}
}

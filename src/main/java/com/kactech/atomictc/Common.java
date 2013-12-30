package com.kactech.atomictc;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Common {
	public static final Charset ASCII = Charset.forName("ASCII");

	public static final Gson gson = new GsonBuilder().registerTypeAdapter(TradeState.Bytes.class,
			new TradeState.Bytes.GsonAdapter()).setPrettyPrinting().create();

	public static String armor(byte[] content, String bookend, boolean lineBreak) {
		return "-----BEGIN " + bookend + "-----\r\n" + new String(base64Encode(content, lineBreak), Common.ASCII)
				+ "-----END " + bookend + "-----";
	}

	public static byte[] base64Encode(byte[] input, boolean lineBreaks) {
		byte[] by = org.apache.commons.codec.binary.Base64.encodeBase64(input);
		if (lineBreaks)
			by = lineBreak(by, 65);
		return by;
	}

	public static byte[] lineBreak(byte[] by, int length) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		int i = 0;
		for (byte b : by) {
			if (i == length) {
				bos.write('\r');
				bos.write('\n');
				i = 0;
			}
			bos.write(b);
			i++;
		}
		if (i > 0) {
			bos.write('\r');
			bos.write('\n');
		}
		return bos.toByteArray();
	}
}

/**
 * 
 */
package org.gamelib.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.StringTokenizer;

import org.gamelib.backend.Backend;
import org.gamelib.backend.Color;
import org.gamelib.backend.Graphics;
import org.gamelib.backend.Image;
import org.gamelib.util.Font.FontImpl;

/** @author pwnedary */
public class BitmapFont extends FontImpl implements Font {
	private static final int LOG2_PAGE_SIZE = 9;
	private static final int PAGE_SIZE = 1 << LOG2_PAGE_SIZE;
	private static final int PAGES = 0x10000 / PAGE_SIZE;
	private int lineHeight;
	private int pages;
	private Image[] pageImgs;
	private Glyph[][] glyphs;

	public BitmapFont(Backend backend, File file) {
		try (InputStream stream = backend.getResourceAsStream(file.getPath());
				BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.forName("UTF-8")))) {
			reader.readLine(); // info
			String[] common = reader.readLine().split(" ", 7);
			lineHeight = Integer.parseInt(common[1].substring(11)); // lineHeight=N
			float base = Integer.parseInt(common[2].substring(5)); // base=N
			pages = Integer.parseInt(common[5].substring(6)); // pages=N
			pageImgs = new Image[pages];
			for (int p = 0; p < pages; p++) {
				String[] page = reader.readLine().split(" ", 3);
				if (Integer.parseInt(page[1].substring(3)) != p) throw new IllegalArgumentException("Invalid font file; page ids should increment from 0."); // id=N
				// File imgFile = new File(file.getParentFile().getPath() + File.separator + page[2].split("\"")[1]);
				File imgFile = new File(file.getParentFile(), page[2].split("\"")[1]); // file=string
				System.out.println(imgFile.getPath());
//				pageImgs[p] = backend.getImage(imgFile);
			}

			float descent = 0;
			reader.readLine(); // chars count=N
			// pageSize = Integer.parseInt(reader.readLine().substring(12)); // chars count=N
			glyphs = new Glyph[PAGES][];
			for (String line = reader.readLine(); line != null && line.startsWith("char "); line = reader.readLine()) {
				Glyph glyph = new Glyph();
				StringTokenizer tokens = new StringTokenizer(line, " =");
				tokens.nextToken();
				tokens.nextToken();
				int c = Integer.parseInt(tokens.nextToken());
				if (c <= Character.MAX_VALUE) setGlyph((char) c, glyph);
				else continue;
				glyph.id = c;
				tokens.nextToken();
				glyph.x = Integer.parseInt(tokens.nextToken());
				tokens.nextToken();
				glyph.y = Integer.parseInt(tokens.nextToken());
				tokens.nextToken();
				glyph.width = Integer.parseInt(tokens.nextToken());
				tokens.nextToken();
				glyph.height = Integer.parseInt(tokens.nextToken());
				tokens.nextToken();
				glyph.xoffset = Integer.parseInt(tokens.nextToken());
				tokens.nextToken();
				glyph.yoffset = Integer.parseInt(tokens.nextToken());
				tokens.nextToken();
				glyph.xadvance = Integer.parseInt(tokens.nextToken());
				tokens.nextToken();
				glyph.page = Integer.parseInt(tokens.nextToken());
				if (glyph.width > 0 && glyph.height > 0) descent = Math.min(base + glyph.yoffset, descent);
			}

			for (String line = reader.readLine(); line != null && line.startsWith("kerning "); line = reader.readLine()) {
				StringTokenizer tokens = new StringTokenizer(line, " =");
				tokens.nextToken();
				tokens.nextToken();
				int first = Integer.parseInt(tokens.nextToken());
				tokens.nextToken();
				int second = Integer.parseInt(tokens.nextToken());
				if (first > Character.MAX_VALUE || second > Character.MAX_VALUE) continue;
				Glyph glyph = getGlyph((char) first);
				tokens.nextToken();
				int amount = Integer.parseInt(tokens.nextToken());
				glyph.setKerning(second, amount);
			}
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid font file.", e);
		}
	}

	private Glyph getGlyph(char c) {
		return glyphs[c / PAGE_SIZE][c & PAGE_SIZE - 1];
	}

	private void setGlyph(char c, Glyph glyph) {
		Glyph[] page = glyphs[c / PAGE_SIZE];
		if (page == null) glyphs[c / PAGE_SIZE] = page = new Glyph[PAGE_SIZE];
		page[c & PAGE_SIZE - 1] = glyph;
	}

	@Override
	public void drawString(Graphics g, String str, int x, int y) {
		Glyph lastGlyph = null;
		int drawX = x;
		int i = 0;
		while (i < str.length()) {
			char c = str.charAt(i++);
			if (c == '\n') {
				drawX = x;
				y += getHeight();
			}
			Glyph glyph = getGlyph(c);
			if (glyph != null) {
				if (lastGlyph != null) drawX += lastGlyph.getKerning(c);
				lastGlyph = glyph;

				int x1 = drawX + glyph.xoffset, y1 = y + glyph.yoffset, x2 = x1 + glyph.width, y2 = y1 + glyph.height;
//				g.setColor(new Color(0, 0, 255, 200));
				g.drawImage(pageImgs[glyph.page], x1, y1, x2, y2, glyph.x, glyph.y, glyph.x + glyph.width, glyph.y + glyph.height);

				Image image = pageImgs[glyph.page];
//				g.drawImage(image, 0, 0, image.getWidth(), image.getHeight(), 0, 0, image.getWidth(), image.getHeight());

				drawX += glyph.xadvance;
			}
		}
	}

	@Override
	public int getWidth(String string) {
		int totalWidth = 0;
		char c, prev = '\u0000';
		for (int i = 0; i < string.length(); i++) {
			c = string.charAt(i);
			Glyph glyph = getGlyph(c);
			totalWidth += glyph.xadvance;
			if (prev != '\u0000') totalWidth += glyph.getKerning(prev);
			prev = c;
		}
		return totalWidth;
	}

	@Override
	public int getHeight() {
		return lineHeight;
	}

	public class Glyph {
		public int id, x, y, width, height, xoffset, yoffset, xadvance, page;
		public byte[][] kerning;

		public int getKerning(char c) {
			if (kerning == null) return 0;
			return kerning[c >>> LOG2_PAGE_SIZE][c & PAGE_SIZE - 1];
		}

		public void setKerning(int c, int amount) {
			if (kerning == null) kerning = new byte[PAGES][];
			byte[] page = kerning[c >>> LOG2_PAGE_SIZE];
			if (page == null) kerning[c >>> LOG2_PAGE_SIZE] = page = new byte[PAGE_SIZE];
			page[c & PAGE_SIZE - 1] = (byte) amount;
		}
	}

}

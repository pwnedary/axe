/**
 * 
 */
package ak5.util;

import java.awt.Color;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import ak5.Platform;
import ak5.graphics.Batch;
import ak5.graphics.Texture;
import ak5.util.Font.FontImpl;
import ak5.util.math.geom.Rectangle;

/** @author pwnedary */
@Deprecated
public class TrueTypeFont extends FontImpl {
	/** Array that holds necessary information about the font characters positions */
	private Rectangle[] glyphs = new Rectangle[256];
	/** Map of user defined font characters (Character <-> Rectangle) */
	private Map<Character, Rectangle> customChars = new HashMap<>();

	/** Boolean flag on whether AntiAliasing is enabled or not */
	private boolean antiAlias;

	/** Font's size */
	private int fontSize = 0;

	/** Font's height */
	private int fontHeight = 0;

	/** Image to cache the font 0-255 characters */
	public Texture fontImage;

	/** Default font texture width */
	private int textureWidth = 512;

	/** Default font texture height */
	private int textureHeight = 512;

	/** A reference to Java's AWT Font that we create our font texture from */
	private java.awt.Font font;

	/** The font metrics for our Java AWT font */
	private FontMetrics fontMetrics;

	private ak5.graphics.Color fontColor;

	private int correctL = 9, correctR = 8;
	int format = ALIGN_LEFT;

	public TrueTypeFont(Platform platform, java.awt.Font font, boolean antiAlias, char[] additionalChars, ak5.graphics.Color fontColor) {
		this.font = font;
		this.fontSize = font.getSize() + 3;
		this.antiAlias = antiAlias;
		this.fontColor = fontColor;

		createSet(platform, additionalChars);

		fontHeight -= 1;
		if (fontHeight <= 0) fontHeight = 1;
	}

	public TrueTypeFont(Platform platform, java.awt.Font font) {
		this(platform, font, true, null, ak5.graphics.Color.BLACK);
	}

	public TrueTypeFont(Platform platform) {
		// this(new java.awt.Font(null, Font.PLAIN, 15), true, null, org.gamelib.util.Color.BLACK);
		// this(getFont(new File("org/gamelib/util/arial.ttf"), PLAIN, DEFAULT_SIZE));
		this(platform, getFont(new File("arial.ttf"), PLAIN, DEFAULT_SIZE));
	}

	public void setCorrection(boolean on) {
		if (on) {
			correctL = 2;
			correctR = 1;
		} else {
			correctL = 0;
			correctR = 0;
		}
	}

	private BufferedImage getFontImage(char ch) {
		// Create a temporary image to extract the character's size
		BufferedImage tempfontImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = (Graphics2D) tempfontImage.getGraphics();
		if (antiAlias == true) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setFont(font);
		fontMetrics = g.getFontMetrics();
		int charwidth = fontMetrics.charWidth(ch) + 8;
		if (charwidth <= 0) charwidth = 7;
		int charheight = fontMetrics.getHeight() + 3;
		if (charheight <= 0) charheight = fontSize;

		// Create another image holding the character we are creating
		BufferedImage fontImage = new BufferedImage(charwidth, charheight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D gt = (Graphics2D) fontImage.getGraphics();
		if (antiAlias == true) gt.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		gt.setFont(font);

		gt.setColor(fontColor.toAWT());
		int charx = 3;
		int chary = 1;
		gt.drawString(String.valueOf(ch), (charx), (chary) + fontMetrics.getAscent());

		return fontImage;

	}

	private void createSet(Platform platform, char[] customCharsArray) {
		// If there are custom chars then I expand the font texture twice
		if (customCharsArray != null && customCharsArray.length > 0) textureWidth *= 2;

		// In any case this should be done in other way. Texture with size 512x512 can maintain only 256 characters with resolution of 32x32. The texture size should be calculated dynamically by looking at character sizes.
		try {
			BufferedImage imgTemp = new BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = (Graphics2D) imgTemp.getGraphics();

			g.setColor(new Color(0, 0, 0, 1));
			g.fillRect(0, 0, textureWidth, textureHeight);

			int rowHeight = 0, positionX = 0, positionY = 0;
			int customCharsLength = (customCharsArray != null) ? customCharsArray.length : 0;

			for (int i = 0; i < 256 + customCharsLength; i++) {
				// get 0-255 characters and then custom characters
				char ch = (i < 256) ? (char) i : customCharsArray[i - 256];

				BufferedImage fontImage = getFontImage(ch);

				Rectangle glyph = new Rectangle();

				glyph.setWidth(fontImage.getWidth());
				glyph.setHeight(fontImage.getHeight());

				if (positionX + glyph.getWidth() >= textureWidth) {
					positionX = 0;
					positionY += rowHeight;
					rowHeight = 0;
				}

				glyph.setX(positionX);
				glyph.setY(positionY);

				if (glyph.getHeight() > fontHeight) fontHeight = glyph.getHeight();
				if (glyph.getHeight() > rowHeight) rowHeight = glyph.getHeight();

				// Draw it here
				g.drawImage(fontImage, positionX, positionY, null);

				positionX += glyph.getWidth();

				if (i < 256) glyphs[i] = glyph;// standard characters
				else customChars.put(new Character(ch), glyph); // custom characters

				fontImage = null;
			}

			//			fontImage = platform.getImage(imgTemp);
		} catch (Exception e) {
			System.err.println("Failed to create font.");
			e.printStackTrace();
		}
	}

	@Override
	public void drawString(Batch batch, String str, int x, int y) {
		Rectangle intObject = null;
		int charCurrent;

		int totalwidth = 0;
		int startIndex = 0, endIndex = str.length() - 1;
		int i = startIndex, d, c;
		float startY = 0;

		float scaleX = 1.0f, scaleY = 1.0f;

		switch (format) {
		case ALIGN_RIGHT: {
			d = -1;
			c = correctR;

			while (i < endIndex)
				if (str.charAt(i++) == '\n') startY += fontHeight;
			break;
		}
		case ALIGN_CENTER: {
			for (int l = startIndex; l <= endIndex; l++) {
				charCurrent = str.charAt(l);
				if (charCurrent == '\n') break;
				if (charCurrent < 256) intObject = glyphs[charCurrent];
				else intObject = (Rectangle) customChars.get(new Character((char) charCurrent));
				totalwidth += intObject.getWidth() - correctL;
			}
			totalwidth /= -2;
		}
		case ALIGN_LEFT:
		default: {
			d = 1;
			c = correctL;
			break;
		}
		}

		while (i >= startIndex && i <= endIndex) {
			charCurrent = str.charAt(i);
			intObject = charCurrent < 256 ? glyphs[charCurrent] : customChars.get(Character.valueOf((char) charCurrent));

			if (intObject != null) {
				if (d < 0) totalwidth += (intObject.getWidth() - c) * d;
				if (charCurrent == '\n') {
					startY += fontHeight * d;
					totalwidth = 0;
					if (format == ALIGN_CENTER) {
						for (int l = i + 1; l <= endIndex; l++) {
							charCurrent = str.charAt(l);
							if (charCurrent == '\n') break;
							if (charCurrent < 256) intObject = glyphs[charCurrent];
							else intObject = (Rectangle) customChars.get(new Character((char) charCurrent));
							totalwidth += intObject.getWidth() - correctL;
						}
						totalwidth /= -2;
					}
					// if center get next lines total width/2;
				} else {
					batch.draw(fontImage, (int) (totalwidth * scaleX + x), (int) (startY * scaleY + y), (int) ((totalwidth + intObject.getWidth()) * scaleX + x), (int) ((startY + intObject.getHeight()) * scaleY + y), intObject.getX(), intObject.getY(), intObject.getX() + intObject.getWidth(), intObject.getY() + intObject.getHeight());
					if (d > 0) totalwidth += (intObject.getWidth() - c) * d;
				}
				i += d;
			}
		}
	}

	@Override
	public int getWidth(String str) {
		int totalwidth = 0;
		Rectangle intObject = null;
		int currentChar = 0;
		for (int i = 0; i < str.length(); i++) {
			currentChar = str.charAt(i);
			intObject = currentChar < 256 ? glyphs[currentChar] : customChars.get(Character.valueOf((char) currentChar));

			if (intObject != null) totalwidth += intObject.getWidth() - correctL;
		}
		return totalwidth;
	}

	@Override
	public int getHeight() {
		return fontHeight;
	}

	public static boolean isSupported(String fontname) {
		for (java.awt.Font font : getFonts())
			if (font.getName().equalsIgnoreCase(fontname)) return true;
		return false;
	}

	public static java.awt.Font[] getFonts() {
		return GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
	}

	public static java.awt.Font getFont(File file, int style, int size) {
		// try (InputStream stream = Game.getBackend().getResourceAsStream(file.getPath())) {
		try (InputStream stream = TrueTypeFont.class.getResourceAsStream(file.getPath())) {
			Map<TextAttribute, Object> fontAttributes = new HashMap<>();
			if ((style & BOLD) == BOLD) fontAttributes.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
			if ((style & ITALIC) == ITALIC) fontAttributes.put(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE);
			if ((style & UNDERLINE) == UNDERLINE) fontAttributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
			if ((style & STRIKETHROUGH) == STRIKETHROUGH) fontAttributes.put(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON);
			fontAttributes.put(TextAttribute.SIZE, new Float(size)); // add size attribute
			java.awt.Font font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, stream).deriveFont(fontAttributes);
			GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
			return font;
		} catch (FontFormatException | IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public void dispose() {}
}

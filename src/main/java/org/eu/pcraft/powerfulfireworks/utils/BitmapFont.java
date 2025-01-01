package org.eu.pcraft.powerfulfireworks.utils;

import lombok.AllArgsConstructor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

@AllArgsConstructor
public class BitmapFont {
    private final Map<Character, int[][]> characters;
    private final int charHeight;

    public int[][] getCharacter(char character) {
        return characters.getOrDefault(character, new int[charHeight][0]);
    }

    public int[][] fromString(String text, int gap) {
        if (text == null || text.isEmpty()) {
            return new int[0][0];
        }

        // First pass: calculate total width
        int totalWidth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int[][] charBitmap = this.getCharacter(c);
            if (charBitmap != null) {
                totalWidth += charBitmap[0].length;
                if (i < text.length() - 1) {
                    totalWidth += gap;
                }
            }
        }

        int[][] resultBitmap = new int[charHeight][totalWidth];
        int currentX = 0;

        // Second pass: copy characters
        for (char c : text.toCharArray()) {
            int[][] charBitmap = this.getCharacter(c);
            if (charBitmap != null) {
                copyCharacterBitmap(resultBitmap, charBitmap, currentX);
                currentX += charBitmap[0].length + gap;
            }
        }

        return resultBitmap;
    }

    private void copyCharacterBitmap(int[][] resultBitmap, int[][] charBitmap, int xOffset) {
        for (int y = 0; y < charBitmap.length; y++) {
            for (int x = 0; x < charBitmap[y].length; x++) {
                resultBitmap[y][xOffset + x] = charBitmap[y][x];
            }
        }
    }

    public static BitmapFont parseBDF(String text) throws IOException {
        Map<Character, int[][]> fontMap = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            Character currentChar = null;
            int[][] currentBitmap = null;
            int bitmapWidth = 0;
            int bitmapHeight = 0;
            int currentRow = 0;
            boolean inBitmap = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Parse character encoding
                if (line.startsWith("ENCODING")) {
                    int charCode = Integer.parseInt(line.split("\\s+")[1]);
                    currentChar = (char) charCode;
                }
                // Parse bitmap dimensions
                else if (line.startsWith("BBX")) {
                    String[] parts = line.split("\\s+");
                    bitmapWidth = Integer.parseInt(parts[1]);
                    bitmapHeight = Integer.parseInt(parts[2]);
                    currentBitmap = new int[bitmapHeight][bitmapWidth];
                }
                // Start of bitmap data
                else if (line.equals("BITMAP")) {
                    inBitmap = true;
                    currentRow = 0;
                }
                // End of bitmap data
                else if (line.equals("ENDCHAR")) {
                    if (currentChar != null && currentBitmap != null) {
                        fontMap.put(currentChar, currentBitmap);
                    }
                    inBitmap = false;
                    currentChar = null;
                    currentBitmap = null;
                }
                // Parse bitmap data
                else if (inBitmap && currentBitmap != null && currentRow < bitmapHeight) {
                    // Convert hex string to binary representation
                    String binaryStr = hexToBinary(line, bitmapWidth);

                    // Fill current row of the bitmap
                    for (int i = 0; i < bitmapWidth; i++) {
                        currentBitmap[currentRow][i] = binaryStr.charAt(i) == '1' ? 1 : 0;
                    }
                    currentRow++;
                }
            }
        }

        return new BitmapFont(fontMap,
                fontMap.values().stream()
                        .mapToInt(bitmap -> bitmap.length)
                        .max()
                        .orElse(0));
    }

    private static String hexToBinary(String hex, int width) {
        // Convert hex string to binary string
        StringBuilder binary = new StringBuilder();
        for (char c : hex.toCharArray()) {
            String bin = Integer.toBinaryString(Integer.parseInt(String.valueOf(c), 16));
            // Pad with leading zeros
            while (bin.length() < 4) {
                bin = "0" + bin;
            }
            binary.append(bin);
        }

        // Trim or pad to match the required width
        if (binary.length() > width) {
            return binary.substring(0, width);
        } else {
            while (binary.length() < width) {
                binary.append("0");
            }
            return binary.toString();
        }
    }
}

package org.eu.pcraft.powerfulfireworks.utils;

import lombok.AllArgsConstructor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

import static java.lang.Math.max;

@AllArgsConstructor
public class BitmapFont {

    public static class CharBitmap{
        CharBitmap(int length){
            ch=new String[length];
        }
        public String[] ch;
    }
    private final Map<Character, CharBitmap> characters;
    private final int charHeight;

    public CharBitmap getCharacter(char character) {
//        System.out.println(Arrays.toString(characters.get(character).ch));
        return characters.getOrDefault(character, new CharBitmap(charHeight));
    }

    public CharBitmap fromString(String text, int gap) {
        if (text == null || text.isEmpty()) {
            return new CharBitmap(0);
        }

//        // First pass: calculate total width
//        int totalWidth = 0;
//        for (int i = 0; i < text.length(); i++) {
//            char c = text.charAt(i);
//            CharBitmap charBitmap = this.getCharacter(c);
//            if (charBitmap != null) {
//                totalWidth += charBitmap.ch[0].length();
//                if (i < text.length() - 1) {
//                    totalWidth += gap;
//                }
//            }
//        }

        CharBitmap resultBitmap = new CharBitmap(charHeight);

        // Second pass: copy characters
        for (char c : text.toCharArray()) {
            CharBitmap charBitmap = this.getCharacter(c);
            if (charBitmap != null) {
                for(int i = 0;i < charHeight;i++){
                    resultBitmap.ch[i]+=charBitmap.ch[i];
                }
//                currentX += charBitmap[0].length + gap;
            }
        }

        return resultBitmap;
    }

    public static BitmapFont parseBDF(String text) throws IOException {
        int maxCurrentRow = 0;
        Map<Character, CharBitmap> fontMap = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            Character currentChar = null;
            CharBitmap currentBitmap = null;
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
                    currentBitmap = new CharBitmap(bitmapHeight);
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
                    currentBitmap.ch[currentRow] = hexToBinary(line, bitmapWidth);
//                    System.out.println(hexToBinary(line, bitmapWidth));
//                    System.out.println(currentBitmap.ch[currentRow]);
                    maxCurrentRow = max(currentRow,maxCurrentRow);
                    currentRow++;
                }
            }
        }
        return new BitmapFont(fontMap, maxCurrentRow);
    }

    private static String hexToBinary(String hex, int width) {
        // Convert hex string to binary string
        StringBuilder binary = new StringBuilder();
        for (char c : hex.toCharArray()) {
            StringBuilder bin = new StringBuilder(Integer.toBinaryString(Integer.parseInt(String.valueOf(c), 16)));
            // Pad with leading zeros
            while (bin.length() < 4) {
                bin.insert(0, "0");
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

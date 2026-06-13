/**
 * The MIT License (MIT)
 * <p>
 * Copyright (c) 2014 nosoop
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package dev.turtywurty.gamedashboard.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.util.Arrays;
import java.util.Map;

/**
 * Provides static methods to convert a file from the Valve Data Format (VDF) to
 * an equivalent JSON representation.
 * <p>
 * Support is basic and disgusting. It also makes certain assumptions of the
 * file (e.g., it assumes every odd-numbered string is a key, while the string
 * to its right is its corresponding value.
 *
 * @author nosoop < nosoop at users.noreply.github.com >
 */
public final class VDFtoJson {
    private VDFtoJson() {
    }

    /**
     * Opening brace character. Used to signal the start of a nested KeyValue
     * set.
     */
    public static final char L_BRACE = '{';
    /**
     * Closing brace character. Used to signal the end of a nested KeyValue set.
     */
    public static final char R_BRACE = '}';
    /**
     * Forward slash character. Used in C++ styled comments.
     */
    public static final char SLASH = '/';
    /**
     * Backward slash character. Used to escape strings.
     */
    public static final char BACK_SLASH = '\\';
    /**
     * Quote character. Used to signal the start of a String (key or value).
     */
    public static final char QUOTE = '"';
    /**
     * Newline character. Essentially whitespace, but we need it when we're
     * skipping C++ styled comments.
     */
    public static final char NEWLINE = '\n';

    /**
     * Attempts to convert what is assumed to be a String containing VDF text
     * into the JSON format.
     *
     * @param string        Input data, assumed to be in the Valve Data Format.
     * @param convertArrays Whether to convert VDF-formatted arrays into
     *                      JsonArrays.
     * @return A JSON representation of the assumed-VDF data.
     * @throws JsonParseException If the input data is malformed.
     */
    public static JsonObject toJSONObject(String string, boolean convertArrays) {
        JsonObject object = new Parser(string).parseObject(false);
        return convertArrays ? convertVDFArrays(object) : object;
    }

    /**
     * Recursively searches for JsonObjects, checking if they should be
     * formatted as arrays, then converted.
     *
     * @param object An input JsonObject converted from VDF.
     * @return JsonObject containing the input JsonObject with objects changed
     * to arrays where applicable.
     */
    private static JsonObject convertVDFArrays(JsonObject object) {
        JsonObject response = new JsonObject();

        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String name = entry.getKey();
            JsonElement value = entry.getValue();

            if (value.isJsonObject()) {
                JsonObject thing = value.getAsJsonObject();

                // Note: Empty JsonObjects are also treated as arrays.
                if (containsVDFArray(thing)) {
                    JsonArray array = new JsonArray();
                    for (int index = 0; index < thing.size(); index++) {
                        array.add(convertVDFArrays(thing.getAsJsonObject(Integer.toString(index))));
                    }
                    response.add(name, array);
                } else {
                    response.add(name, convertVDFArrays(thing));
                }
            } else {
                response.add(name, value);
            }
        }

        return response;
    }

    /**
     * Checks that a JsonObject converted from a VDF file is an array. If so,
     * the only keys in the JsonObject are a continuous set of integers
     * represented by Strings starting from "0". Note that empty JsonObjects are
     * also treated as arrays.
     *
     * @param object The JsonObject to check for a VDF-formatted array.
     * @return Whether the JsonObject is a VDF-formatted array.
     */
    private static boolean containsVDFArray(JsonObject object) {
        int indices = object.size();
        int[] index = new int[indices];

        Arrays.fill(index, -1);

        /*
          Fail if we encounter a non-integer, if a value isn't a JsonObject,
          or if the key is a number that is larger than the size of the array
          (meaning we're missing a value).
         */
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                return false;
            }

            try {
                int i = Integer.parseInt(entry.getKey());

                if (i < 0 || i >= indices) {
                    return false;
                }

                index[i] = i;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        // Fail if we are missing any values (e.g., 0, 1, 2, 3, 4, 5, 7, 8, 9).
        for (int i = 0; i < indices; i++) {
            if (index[i] != i) {
                return false;
            }
        }

        return true;
    }

    private static final class Parser {
        private final String input;
        private int position;

        private Parser(String input) {
            this.input = input;
        }

        private JsonObject parseObject(boolean nested) {
            JsonObject object = new JsonObject();

            while (true) {
                skipWhitespaceAndComments();
                if (!hasNext()) {
                    if (nested) {
                        throw error("Unterminated object");
                    }

                    return object;
                }

                char firstChar = next();
                if (firstChar == R_BRACE) {
                    if (!nested) {
                        throw error("Unexpected character '" + firstChar + "'");
                    }

                    return object;
                }

                if (firstChar == '\0') {
                    continue;
                }

                if (firstChar != QUOTE) {
                    throw error("Unexpected character '" + firstChar + "'");
                }

                String key = parseString();
                skipWhitespaceAndComments();
                if (!hasNext()) {
                    throw error("Missing value for key \"" + key + "\"");
                }

                char valueStart = next();
                if (valueStart == QUOTE) {
                    object.addProperty(key, parseString());
                } else if (valueStart == L_BRACE) {
                    object.add(key, parseObject(true));
                } else {
                    throw error("Unexpected character '" + valueStart + "'");
                }
            }
        }

        private String parseString() {
            StringBuilder value = new StringBuilder();

            while (hasNext()) {
                char character = next();
                if (character == QUOTE) {
                    return value.toString();
                }

                if (character != BACK_SLASH) {
                    value.append(character);
                    continue;
                }

                if (!hasNext()) {
                    throw error("Unterminated escape sequence");
                }

                char escaped = next();
                switch (escaped) {
                    case 'n':
                        value.append('\n');
                        break;
                    case 't':
                        value.append('\t');
                        break;
                    case BACK_SLASH:
                    case QUOTE:
                        value.append(escaped);
                        break;
                    default:
                        throw error("Unexpected escape sequence \"\\" + escaped + "\"");
                }
            }

            throw error("Unterminated string");
        }

        private void skipWhitespaceAndComments() {
            while (hasNext()) {
                char character = input.charAt(position);
                if (Character.isWhitespace(character)) {
                    position++;
                    continue;
                }

                if (character == SLASH
                        && position + 1 < input.length()
                        && input.charAt(position + 1) == SLASH) {
                    position += 2;
                    while (hasNext() && input.charAt(position) != NEWLINE) {
                        position++;
                    }
                    continue;
                }

                return;
            }
        }

        private boolean hasNext() {
            return position < input.length();
        }

        private char next() {
            return input.charAt(position++);
        }

        private JsonParseException error(String message) {
            return new JsonParseException(message + " at character " + position);
        }
    }
}

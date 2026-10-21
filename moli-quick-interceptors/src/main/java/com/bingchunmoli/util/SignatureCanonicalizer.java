package com.bingchunmoli.util;

import com.bingchunmoli.exception.SignParamIllegalArgumentException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;

import java.io.IOException;
import java.util.Objects;
import java.util.stream.Collectors;

/** Escapes signature component boundaries while preserving ordinary legacy text. */
public final class SignatureCanonicalizer {
    private static final JsonFactory STRICT_JSON = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    private SignatureCanonicalizer() { }

    public static String encodeComponent(String value) {
        return Objects.requireNonNull(value, "signature component")
                .replace("%", "%25")
                .replace("&", "%26")
                .replace("=", "%3D")
                .replace("\r", "%0D")
                .replace("\n", "%0A");
    }

    /**
     * For a signature carried in JSON, sign the same JSON with that field's string value set to "".
     * Preserve every other character (apart from the existing protocol's line joining), including
     * whitespace, property order and number spelling. The signature name must occur exactly once.
     */
    public static String jsonBodyForSigning(String body, String signatureName) {
        String text = Objects.requireNonNull(body, "JSON body").lines().collect(Collectors.joining());
        int start = -1;
        int end = -1;
        int depth = 0;
        int roots = 0;
        try (var parser = STRICT_JSON.createParser(text)) {
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                if (token == JsonToken.FIELD_NAME && signatureName.equals(parser.currentName())) {
                    if (start >= 0 || parser.nextToken() != JsonToken.VALUE_STRING) {
                        throw new SignParamIllegalArgumentException("JSON must contain exactly one string signature field");
                    }
                    start = Math.toIntExact(parser.currentTokenLocation().getCharOffset());
                    parser.getText(); // Finish the lazily parsed string before taking its end offset.
                    end = Math.toIntExact(parser.currentLocation().getCharOffset());
                } else if (token.isStructStart()) {
                    if (depth++ == 0) roots++;
                } else if (token.isStructEnd()) {
                    depth--;
                } else if (token.isScalarValue() && depth == 0) {
                    roots++;
                }
            }
        } catch (IOException invalid) {
            throw new SignParamIllegalArgumentException("Invalid JSON signature body");
        }
        if (roots != 1 || depth != 0 || start < 0) {
            throw new SignParamIllegalArgumentException("JSON must contain exactly one string signature field");
        }
        return text.substring(0, start) + "\"\"" + text.substring(end);
    }
}

package org.dllearner.algorithms.isle.index;

import java.util.LinkedList;

/**
 * A simple text document without further formatting or markup.
 *
 * @author Daniel Fleischhacker
 */
public class TextDocument extends LinkedList<Token> implements Document {
    @Override
    public String getContent() {
        return getContentStartingAtToken(this.getFirst(), SurfaceFormLevel.STEMMED);
    }

    @Override
    public String getRawContent() {
        return getContentStartingAtToken(this.getFirst(), SurfaceFormLevel.RAW);
    }

    @Override
    public String getPOSTaggedContent() {
        return getContentStartingAtToken(this.getFirst(), SurfaceFormLevel.POS_TAGGED);
    }

    /**
     * Returns a string containing all tokens starting at the token {@code start} until the end of the list. The
     * surface forms according to {@code level} are used to build the string.
     *
     * @param start token to start building the string at, i.e., the first token in the returned string
     * @param l level of surface forms to use
     * @return built string
     */
    public String getContentStartingAtToken(Token start, SurfaceFormLevel l) {
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        for (Token t : this) {
            if (found) {
                sb.append(" ");
                String surfaceForm = getStringForLevel(t, l);
                if (surfaceForm != null) {
                    sb.append(surfaceForm);
                }
            }
            else if (t == start) {
                found = true;
                sb.append(getStringForLevel(t, l));
            }
        }

        return sb.toString();
    }

    private String getStringForLevel(Token t, SurfaceFormLevel l) {
        switch (l) {
            case RAW:
                return t.getRawForm();
            case POS_TAGGED:
                return t.getPOSTag();
            case STEMMED:
                return t.isPunctuation() ? null : t.getStemmedForm();
        }

        return null;
    }

    public static void main(String[] args) {
        TextDocument t = new TextDocument();
        String s = "This is a very long, nice text for testing our new implementation of TextDocument.";
        for (String e : s.split(" ")) {
            t.add(new Token(e));
        }

        System.out.println(t.getRawContent());
    }
}

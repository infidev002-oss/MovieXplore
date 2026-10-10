package com.movies.moviexplore.textview;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.ViewGroup;

import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TextViewStyler extends TextView {
    private static final String HAIR_SPACE = "\u200A";
    private static final String NORMAL_SPACE = " ";

    private int viewWidth;

    private final List<String> lines = new ArrayList<>();
    private final List<String> currentLine = new ArrayList<>();
    private final List<String> workingWithSpaces = new ArrayList<>();

    private String justifiedText = "";
    private final Random random = new Random();

    public TextViewStyler(Context context) {
        super(context);
    }

    public TextViewStyler(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TextViewStyler(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        CharSequence src = getText();
        if (src == null) {
            super.onDraw(canvas);
            return;
        }

        String raw = src.toString();
        if (!justifiedText.equals(raw)) {
            ViewGroup.LayoutParams params = getLayoutParams();
            viewWidth = getMeasuredWidth() - (getPaddingLeft() + getPaddingRight());

            if (params.width != ViewGroup.LayoutParams.WRAP_CONTENT && viewWidth > 0 && !raw.isEmpty()) {
                justifiedText = justify(raw);
                if (!justifiedText.isEmpty()) {
                    setText(justifiedText);
                    lines.clear();
                    currentLine.clear();
                } else {
                    super.onDraw(canvas);
                }
            } else {
                super.onDraw(canvas);
            }
        } else {
            super.onDraw(canvas);
        }
    }

    private String justify(String text) {
        // Normalize and make '\n' its own token
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n").replace("\n", " \n ");
        String[] tokens = normalized.split(" ");

        for (String token : tokens) {
            if (token.isEmpty()) continue;

            if ("\n".equals(token)) {
                if (!currentLine.isEmpty()) {
                    lines.add(joinWords(currentLine, true)); // ragged-right for explicit breaks
                    currentLine.clear();
                }
                lines.add("\n"); // paragraph separator / blank line
                continue;
            }

            if (fitsInLine(token, currentLine, true)) {
                currentLine.add(token);
            } else {
                if (!currentLine.isEmpty()) {
                    lines.add(justifyLine(currentLine));
                    currentLine.clear();
                }
                currentLine.add(token);
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(joinWords(currentLine, true)); // last line is ragged-right
            currentLine.clear();
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String ln = lines.get(i);
            if ("\n".equals(ln)) {
                out.append("\n");
            } else {
                out.append(ln);
                if (i + 1 < lines.size() && !"\n".equals(lines.get(i + 1))) {
                    out.append("\n");
                }
            }
        }
        return out.toString();
    }

    private String joinWords(List<String> words, boolean spacesBetween) {
        if (words.isEmpty()) return "";
        if (!spacesBetween) {
            StringBuilder sb = new StringBuilder();
            for (String w : words) sb.append(w);
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            sb.append(words.get(i));
            if (i < words.size() - 1) sb.append(NORMAL_SPACE);
        }
        return sb.toString();
    }

    private String justifyLine(List<String> words) {
        workingWithSpaces.clear();

        if (words.size() <= 1) {
            return joinWords(words, true);
        }

        // seed as [word, SPACE, word, SPACE, ...]
        for (int i = 0; i < words.size(); i++) {
            workingWithSpaces.add(words.get(i));
            if (i < words.size() - 1) {
                workingWithSpaces.add(NORMAL_SPACE);
            }
        }

        // Use renamed helper to avoid signature clash
        while (fitsWithExtra(HAIR_SPACE, workingWithSpaces, false)) {
            int insertAt = getRandomBetween(1, Math.max(1, workingWithSpaces.size() - 1));
            workingWithSpaces.add(insertAt, HAIR_SPACE);
        }

        return joinWords(workingWithSpaces, false);
    }

    /**
     * Will the 'word' fit if added as the next token to 'line'?
     * When spacesBetween=true, we simulate a single space BETWEEN words only (no trailing).
     */
    private boolean fitsInLine(String word, List<String> line, boolean spacesBetween) {
        String base = joinWords(line, spacesBetween);
        String candidate;
        if (base.isEmpty()) {
            candidate = word;
        } else if (spacesBetween) {
            candidate = base + NORMAL_SPACE + word;
        } else {
            candidate = base + word;
        }
        float w = getPaint().measureText(candidate);
        return w <= viewWidth; // <= avoids premature wrap
    }

    /**
     * Will 'extra' still fit if appended to an already composed line (which may already contain spaces)?
     * This is used when sprinkling HAIR_SPACE.
     */
    private boolean fitsWithExtra(String extra, List<String> composed, boolean composedHasBetweenSpaces) {
        String base = joinWords(composed, composedHasBetweenSpaces);
        String candidate = base + extra;
        float w = getPaint().measureText(candidate);
        return w <= viewWidth;
    }

    private int getRandomBetween(int inclusiveMin, int exclusiveMax) {
        if (exclusiveMax <= inclusiveMin) return inclusiveMin;
        return inclusiveMin + random.nextInt(exclusiveMax - inclusiveMin);
    }
}


package io.github.rosemoe.sora.util;

import android.graphics.Color;
import android.util.Log;

import com.pranav.common.util.DiagnosticWrapper;

import io.github.rosemoe.sora.lang.styling.Span;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.widget.CodeEditor;

import java.util.ArrayList;
import java.util.List;

import javax.tools.Diagnostic;

public class HighlightUtil {

    public static void markProblemRegion(
            Styles styles,
            int newFlag,
            int startLine,
            int startColumn,
            int endLine,
            int endColumn
    ) {
        for (int line = startLine; line <= endLine; line++) {
            int start = (line == startLine ? startColumn : 0);
            int end = (line == endLine ? endColumn : Integer.MAX_VALUE);
            var read = styles.getSpans().read();
            var spans = new ArrayList<Span>(read.getSpansOnLine(line));
            int increment;
            for (int i = 0; i < spans.size(); i += increment) {
                var span = spans.get(i);
                increment = 1;
                if (span.column >= end) {
                    break;
                }
                int spanEnd = (i + 1 >= spans.size() ? Integer.MAX_VALUE : spans.get(i + 1).column);
                if (spanEnd >= start) {
                    int regionStartInSpan = Math.max(span.column, start);
                    int regionEndInSpan = Math.min(end, spanEnd);
                    if (regionStartInSpan == span.column) {
                        if (regionEndInSpan != spanEnd) {
                            increment = 2;
                            var nSpan = span.copy();
                            nSpan.column = regionEndInSpan;
                            spans.add(i + 1, nSpan);
                        }
                        span.problemFlags |= newFlag;
                    } else {
                        // regionStartInSpan > span.column
                        if (regionEndInSpan == spanEnd) {
                            increment = 2;
                            var nSpan = span.copy();
                            nSpan.column = regionStartInSpan;
                            spans.add(i + 1, nSpan);
                            nSpan.problemFlags |= newFlag;
                        } else {
                            increment = 3;
                            var span1 = span.copy();
                            span1.column = regionStartInSpan;
                            span1.problemFlags |= newFlag;
                            var span2 = span.copy();
                            span2.column = regionEndInSpan;
                            spans.add(i + 1, span1);
                            spans.add(i + 2, span2);
                        }
                    }
                }
            }

            var modify = styles.getSpans().modify();
            modify.setSpansOnLine(line, spans);
        }
    }

    /**
     * Highlights the list of given diagnostics, taking care of conversion between 1-based offsets
     * to 0-based offsets. It also makes the Diagnostic eligible for shifting as the user types.
     */
    public static void markDiagnostics(
            CodeEditor editor, List<DiagnosticWrapper> diagnostics, Styles styles) {
        diagnostics.forEach(
                it -> {
                    try {
                        int startLine;
                        int startColumn;
                        int endLine;
                        int endColumn;
                        if (it.getPosition() != DiagnosticWrapper.USE_LINE_POS) {
                            if (it.getStartPosition() == -1) {
                                it.setStartPosition(it.getPosition());
                            }
                            if (it.getEndPosition() == -1) {
                                it.setEndPosition(it.getPosition());
                            }

                            if (it.getStartPosition() > editor.getText().length()) {
                                return;
                            }
                            if (it.getEndPosition() > editor.getText().length()) {
                                return;
                            }
                            var start =
                                    editor.getCursor()
                                            .getIndexer()
                                            .getCharPosition((int) it.getStartPosition());
                            var end =
                                    editor.getCursor()
                                            .getIndexer()
                                            .getCharPosition((int) it.getEndPosition());

                            int sLine = start.getLine();
                            int sColumn = start.getColumn();
                            int eLine = end.getLine();
                            int eColumn = end.getColumn();

                            // the editor does not support marking underline spans for the same
                            // start and end
                            // index
                            // to work around this, we just subtract one to the start index
                            if (sLine == eLine && eColumn == sColumn) {
                                sColumn--;
                                eColumn++;
                            }

                            it.setStartLine(sLine);
                            it.setEndLine(eLine);
                            it.setStartColumn(sColumn);
                            it.setEndColumn(eColumn);
                        }
                        startLine = it.getStartLine();
                        startColumn = it.getStartColumn();
                        endLine = it.getEndLine();
                        endColumn = it.getEndColumn();

                        int flag =
                                it.getKind() == Diagnostic.Kind.ERROR
                                        ? Span.FLAG_ERROR
                                        : Span.FLAG_WARNING;
                        markProblemRegion(styles, flag, startLine, startColumn, endLine, endColumn);
                    } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                        Log.d("HighlightUtil", "Failed to mark diagnostics", e);
                    }
                });
        editor.setStyles(editor.getEditorLanguage().getAnalyzeManager(), styles);
    }

    public static void clearDiagnostics(Styles styles) {
        var spans = styles.getSpans();
        var read = spans.read();
        for (int i = 0; i < spans.getLineCount(); i++) {
            List<Span> original;
            try {
                original = read.getSpansOnLine(i);
            } catch (NullPointerException e) {
                continue;
            }
            var spansOnLine = new ArrayList<Span>(original);
            for (var span : spansOnLine) {
                span.problemFlags = 0;
            }
            spans.modify().setSpansOnLine(i, spansOnLine);
        }
    }
}
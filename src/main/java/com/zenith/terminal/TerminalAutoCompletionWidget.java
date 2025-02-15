package com.zenith.terminal;

import org.jline.reader.LineReader;
import org.jline.reader.Widget;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.widget.Widgets;

import static com.zenith.Shared.TERMINAL_LOG;

public class TerminalAutoCompletionWidget extends Widgets {
    private final Widget backwardDeleteOrig;
    private final Widget deleteCharOrig;
    private final Widget selfInsertOrig;

    public TerminalAutoCompletionWidget(final LineReader reader) {
        super(reader);
        aliasWidget("." + LineReader.BACKWARD_DELETE_CHAR, LineReader.BACKWARD_DELETE_CHAR);
        aliasWidget("." + LineReader.DELETE_CHAR, LineReader.DELETE_CHAR);
        aliasWidget("." + LineReader.SELF_INSERT, LineReader.SELF_INSERT);
        backwardDeleteOrig = reader.getWidgets().get(LineReader.BACKWARD_DELETE_CHAR);
        deleteCharOrig = reader.getWidgets().get(LineReader.DELETE_CHAR);
        selfInsertOrig = reader.getWidgets().get(LineReader.SELF_INSERT);
        addWidget(LineReader.BACKWARD_DELETE_CHAR, this::onBackwardsDelete);
        addWidget(LineReader.DELETE_CHAR, this::onDeleteChar);
        addWidget(LineReader.SELF_INSERT, this::onSelfInsert);
    }

    private boolean onSelfInsert() {
        var o = selfInsertOrig.apply();
        if (o) {
            callWidget(LineReader.LIST_CHOICES);
        }
        return o;
    }

    private boolean onDeleteChar() {
        var o = deleteCharOrig.apply();
        if (o) {
            if (reader.getBuffer().length() > 0) {
                callWidget(LineReader.LIST_CHOICES);
            } else {
                clearListChoices();
            }
        }
        return o;
    }

    private boolean onBackwardsDelete() {
        var o = backwardDeleteOrig.apply();
        if (o) {
            if (reader.getBuffer().length() > 0) {
                callWidget(LineReader.LIST_CHOICES);
            } else {
                clearListChoices();
            }
        }
        return o;
    }

    private void clearListChoices() {
        try {
            // if we do not do this and the command buffer is empty, the choices will still be displayed
            var post = LineReaderImpl.class.getDeclaredField("post");
            post.setAccessible(true);
            post.set(reader, null);
        } catch (Exception e) {
            TERMINAL_LOG.error("Failed to clear choices", e);
        }
    }
}

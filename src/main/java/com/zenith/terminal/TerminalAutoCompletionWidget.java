package com.zenith.terminal;

import org.jline.reader.LineReader;
import org.jline.reader.Widget;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.widget.Widgets;

import java.util.concurrent.locks.ReentrantLock;

import static com.zenith.Shared.TERMINAL_LOG;

public class TerminalAutoCompletionWidget extends Widgets {
    private final Widget backwardDeleteOrig;
    private final Widget deleteCharOrig;
    private final Widget selfInsertOrig;
    private final Widget expandOrCompleteOrig;

    public TerminalAutoCompletionWidget(final LineReader reader) {
        super(reader);
        aliasWidget("." + LineReader.BACKWARD_DELETE_CHAR, LineReader.BACKWARD_DELETE_CHAR);
        aliasWidget("." + LineReader.DELETE_CHAR, LineReader.DELETE_CHAR);
        aliasWidget("." + LineReader.SELF_INSERT, LineReader.SELF_INSERT);
        aliasWidget("." + LineReader.EXPAND_OR_COMPLETE, LineReader.EXPAND_OR_COMPLETE);
        backwardDeleteOrig = reader.getWidgets().get(LineReader.BACKWARD_DELETE_CHAR);
        deleteCharOrig = reader.getWidgets().get(LineReader.DELETE_CHAR);
        selfInsertOrig = reader.getWidgets().get(LineReader.SELF_INSERT);
        expandOrCompleteOrig = reader.getWidgets().get(LineReader.EXPAND_OR_COMPLETE);
        addWidget(LineReader.BACKWARD_DELETE_CHAR, this::onBackwardsDelete);
        addWidget(LineReader.DELETE_CHAR, this::onDeleteChar);
        addWidget(LineReader.SELF_INSERT, this::onSelfInsert);
        addWidget(LineReader.EXPAND_OR_COMPLETE, this::onExpandOrComplete);
    }

    private boolean onExpandOrComplete() {
        return completionHandler(expandOrCompleteOrig);
    }

    private boolean onSelfInsert() {
        return completionHandler(selfInsertOrig);
    }

    private boolean onDeleteChar() {
        return completionHandler(deleteCharOrig);
    }

    private boolean onBackwardsDelete() {
        return completionHandler(backwardDeleteOrig);
    }

    private boolean completionHandler(Widget orig) {
        var o = orig.apply();
        if (o) {
            if (reader.getBuffer().length() > 0) {
                if (!callListChoices()) {
                    clearListChoices();
                }
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

    // built-in callWidget method does not return the widget's return value which we need
    private boolean callListChoices() {
        try {
            var w = reader.getBuiltinWidgets().get(LineReader.LIST_CHOICES);
            if (w == null) return true;
            var lockField = LineReaderImpl.class.getDeclaredField("lock");
            lockField.setAccessible(true);
            var lock = (ReentrantLock) lockField.get(reader);
            lock.lock();
            try {
                return w.apply();
            } finally {
                lock.unlock();
            }
        } catch (final Exception e) {
            TERMINAL_LOG.error("Failed to call built-in widget: {}", LineReader.LIST_CHOICES, e);
        }
        return true;
    }
}

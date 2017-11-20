package org.apache.jenkins.gitpubsub;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

public class TimestampMatcher extends BaseMatcher<Long> {
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat(ASFGitSCMNavigator.RFC_2822);
    private final long expected;

    public static TimestampMatcher timestamp(long expected) {
        return new TimestampMatcher(expected);
    }

    public static TimestampMatcher timestamp(Date expected) {
        return new TimestampMatcher(expected);
    }

    public static TimestampMatcher timestamp(String rfc2822) throws ParseException {
        return new TimestampMatcher(rfc2822);
    }

    public TimestampMatcher(long expected) {
        this.expected = expected;
    }

    public TimestampMatcher(Date expected) {
        this.expected = expected.getTime();
    }

    public TimestampMatcher(String rfc2822) throws ParseException {
        this.expected = FORMAT.parse(rfc2822).getTime();
    }

    @Override
    public boolean matches(Object item) {
        return expected == (Long)item;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("timestamp of ").appendValue(FORMAT.format(expected));
    }

    @Override
    public void describeMismatch(Object item, Description description) {
        if (item instanceof Long) {
            description.appendText("was timestamp of ").appendValue(FORMAT.format(item));
        } else {
            super.describeMismatch(item, description);
        }
    }
}

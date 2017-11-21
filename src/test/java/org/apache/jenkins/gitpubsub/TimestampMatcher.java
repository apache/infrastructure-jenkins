/*
 * Copyright 2017 Stephen Connolly.
 *
 * Licensed under the Apache License,Version2.0(the"License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,software
 * distributed under the License is distributed on an"AS IS"BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

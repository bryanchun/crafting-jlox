package craftinglox.lox

import java.lang.RuntimeException

/**
 * Return value mechanism to cascade back up the stack to the function scope.
 */
class Return(val value: Any?) : RuntimeException(
    // Since this exception is used for a control flow implementation
    // Disable JVM exception stacktrace etc.
    null, null, false, false,
)
mvn checkstyle:checkstyle > /dev/null 2>&1
mvn javadoc:javadoc 2>&1 | grep "WARNING"

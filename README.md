README
======

Source code

+ $ svn co http://svn.apache.org/repos/asf/xmlgraphics/commons/trunk/ commons
+ $ svn co http://svn.apache.org/repos/asf/xmlgraphics/batik/trunk/ batik
+ $ svn co http://svn.apache.org/repos/asf/xmlgraphics/fop/trunk fop

Clean-up:
 $ find . -type d -name '.svn' -exec rm -Rf {} \;
 $ find . -type f -name '.svnignore' -exec rm {} \;
 $ find -type f -not -name 'package.html' -not -name 'overview.html' -not -name '*.java' | xargs -i{} cp --parents {} ../resources
 $ find -type f -not -name 'package.html' -not -name 'overview.html' -not -name '*.java' -exec rm {} \;


Replaced dependency on Xalan and Xerces with default implementations provided by JRE

Java 6 Xalan 2.6.0

/usr/lib/jvm/java-8-oracle/bin/java com.sun.org.apache.xalan.internal.xslt.EnvironmentCheck




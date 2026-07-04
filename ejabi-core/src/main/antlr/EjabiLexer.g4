lexer grammar EjabiLexer;

OPEN_RAW           : '<%==' -> pushMode(TAG_MODE);
OPEN_EXPR          : '<%=' -> pushMode(TAG_MODE);
OPEN_COMMENT       : '<%#' -> pushMode(TAG_MODE);
LITERAL_OPEN       : '<%%';
OPEN_CODE_TRIM     : '<%-' -> pushMode(TAG_MODE);
OPEN_CODE          : '<%' -> pushMode(TAG_MODE);
TEXT               : (~'<' | '<' ~'%')+;
LT                 : '<';

mode TAG_MODE;

CLOSE_TRIM         : '-%>' -> popMode;
CLOSE_EQ_TRIM      : '=%>' -> popMode;
CLOSE              : '%>' -> popMode;
TAG_CONTENT        : (~[%\-='"] | '%' ~'>' | '-' ~'%' | '=' ~'%' | QUOTED)+;

fragment QUOTED    : '"' ('\\' . | ~["\\])* '"'
                   | '\'' ('\\' . | ~['\\])* '\''
                   ;

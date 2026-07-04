parser grammar EjabiParser;

options {
    tokenVocab = EjabiLexer;
}

template
    : element* EOF
    ;

element
    : TEXT
    | LT
    | LITERAL_OPEN
    | escapedExpression
    | rawExpression
    | codeBlock
    | commentBlock
    ;

codeBlock
    : (OPEN_CODE | OPEN_CODE_TRIM) TAG_CONTENT? closeTag
    ;

escapedExpression
    : OPEN_EXPR TAG_CONTENT? closeTag
    ;

rawExpression
    : OPEN_RAW TAG_CONTENT? closeTag
    ;

commentBlock
    : OPEN_COMMENT TAG_CONTENT? closeTag
    ;

closeTag
    : CLOSE
    | CLOSE_TRIM
    | CLOSE_EQ_TRIM
    ;

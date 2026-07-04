grammar EjabiExpr;

// Expression language for Ejabi template tags. Parsed separately from the
// template grammar: template tags hand their content string to this grammar.
// Precedence follows the rule order (loosest first); comparisons and
// equalities are non-associative, so `a < b < c` is a syntax error.

expression
    : ternaryExpression EOF
    ;

ternaryExpression
    : orExpression (QUESTION ternaryExpression COLON ternaryExpression)?
    ;

orExpression
    : andExpression (OR andExpression)*
    ;

andExpression
    : equalityExpression (AND equalityExpression)*
    ;

equalityExpression
    : comparisonExpression ((EQ | NE) comparisonExpression)?
    ;

comparisonExpression
    : additiveExpression ((GE | LE | GT | LT) additiveExpression)?
    ;

additiveExpression
    : multiplicativeExpression ((PLUS | MINUS) multiplicativeExpression)*
    ;

multiplicativeExpression
    : unaryExpression ((STAR | SLASH | PERCENT) unaryExpression)*
    ;

unaryExpression
    : NOT unaryExpression
    | MINUS unaryExpression
    | PLUS unaryExpression
    | postfixExpression
    ;

postfixExpression
    : primaryExpression postfixPart*
    ;

postfixPart
    : DOT IDENT
    | LBRACKET ternaryExpression RBRACKET
    ;

primaryExpression
    : STRING
    | NUMBER
    | TRUE
    | FALSE
    | NULL
    | IDENT
    | LPAREN ternaryExpression RPAREN
    ;

OR       : '||' ;
AND      : '&&' ;
EQ       : '==' ;
NE       : '!=' ;
GE       : '>=' ;
LE       : '<=' ;
GT       : '>' ;
LT       : '<' ;
PLUS     : '+' ;
MINUS    : '-' ;
STAR     : '*' ;
SLASH    : '/' ;
PERCENT  : '%' ;
NOT      : '!' ;
QUESTION : '?' ;
COLON    : ':' ;
DOT      : '.' ;
LBRACKET : '[' ;
RBRACKET : ']' ;
LPAREN   : '(' ;
RPAREN   : ')' ;
TRUE     : 'true' ;
FALSE    : 'false' ;
NULL     : 'null' ;
IDENT    : [a-zA-Z_$] [a-zA-Z0-9_$]* ;
NUMBER   : [0-9]+ ('.' [0-9]+)? ;
STRING   : '\'' (ESC | ~['\\])* '\''
         | '"' (ESC | ~["\\])* '"'
         ;

fragment ESC : '\\' . ;

WS : [ \t\r\n]+ -> skip ;

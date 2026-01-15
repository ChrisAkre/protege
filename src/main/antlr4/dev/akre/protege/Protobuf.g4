/*
 * Protocol Buffers (proto2 and proto3) grammar for ANTLR v4
 *
 * Based on the official Protocol Buffers Language Specification:
 * https://developers.google.com/protocol-buffers/docs/reference/proto3-spec
 * https://developers.google.com/protocol-buffers/docs/reference/proto2-spec
 */

grammar Protobuf;

// ====== Parser Rules ======

proto
    : syntax ( importStatement
             | packageStatement
             | option
             | topLevelDef
             | emptyStatement_
             )* EOF
    ;

syntax
    : SYNTAX EQUAL ( protoVersion ) SEMICOLON
    ;

protoVersion
    : PROTO2 | PROTO3
    ;

importStatement
    : IMPORT ( WEAK | PUBLIC )? strLit SEMICOLON
    ;

packageStatement
    : PACKAGE fullIdent SEMICOLON
    ;

option
    : OPTION optionName EQUAL constant SEMICOLON
    ;

optionName
    : ident ( DOT ident )*
    | LPAREN custom=ident RPAREN ( DOT ident )*
    ;

topLevelDef
    : messageDef
    | enumDef
    | serviceDef
    | extend
    ;

// Message definition
messageDef
    : MESSAGE messageName messageBody
    ;

messageBody
    : LBRACE ( field
            | enumDef
            | messageDef
            | extend
            | extensions
            | group
            | option
            | oneof
            | mapField
            | reserved
            | emptyStatement_
            )* RBRACE
    ;

// Enum definition
enumDef
    : ENUM enumName enumBody
    ;

enumBody
    : LBRACE ( option
            | enumField
            | emptyStatement_
            )* RBRACE
    ;

enumField
    : ident EQUAL ( MINUS )? intLit ( LBRACK enumValueOption ( COMMA enumValueOption )* RBRACK )? SEMICOLON
    ;

enumValueOption
    : optionName EQUAL constant
    ;

// Service definition
serviceDef
    : SERVICE serviceName LBRACE ( option
                                 | rpc
                                 | emptyStatement_
                                 )* RBRACE
    ;

rpc
    : RPC rpcName LPAREN ( STREAM )? messageType RPAREN RETURNS LPAREN ( STREAM )? messageType RPAREN
      ( ( LBRACE ( option | emptyStatement_ )* RBRACE ) | SEMICOLON )
    ;

// Field
field
    : ( REQUIRED | OPTIONAL | REPEATED )? type_ fieldName EQUAL fieldNumber ( LBRACK fieldOptions RBRACK )? SEMICOLON
    ;

fieldOptions
    : option ( COMMA option )*
    ;

fieldNumber
    : intLit
    ;

// Oneof
oneof
    : ONEOF oneofName LBRACE ( oneofField | emptyStatement_ )* RBRACE
    ;

oneofField
    : type_ fieldName EQUAL fieldNumber ( LBRACK fieldOptions RBRACK )? SEMICOLON
    ;

// Map field
mapField
    : MAP LANGLE keyType COMMA type_ RANGLE mapName EQUAL fieldNumber ( LBRACK fieldOptions RBRACK )? SEMICOLON
    ;

keyType
    : INT32 | INT64 | UINT32 | UINT64 | SINT32 | SINT64
    | FIXED32 | FIXED64 | SFIXED32 | SFIXED64 | BOOL | STRING
    ;

// Type
type_
    : DOUBLE | FLOAT | INT32 | INT64 | UINT32 | UINT64
    | SINT32 | SINT64 | FIXED32 | FIXED64 | SFIXED32 | SFIXED64
    | BOOL | STRING | BYTES | messageType
    ;

messageType
    : ( DOT )? ( ident DOT )* messageName
    ;

// Extensions
extensions
    : EXTENSIONS ranges SEMICOLON
    ;

ranges
    : range ( COMMA range )*
    ;

range
    : intLit ( TO ( intLit | MAX ) )?
    ;

// Extend
extend
    : EXTEND messageType LBRACE ( field | group | emptyStatement_ )* RBRACE
    ;

// Group (proto2 only)
group
    : ( REQUIRED | OPTIONAL | REPEATED )? GROUP groupName EQUAL fieldNumber messageBody
    ;

// Reserved
reserved
    : RESERVED ( ranges | fieldNames ) SEMICOLON
    ;

fieldNames
    : strLit ( COMMA strLit )*
    ;

// Constants
constant
    : fullIdent
    | intLit
    | floatLit
    | strLit
    | boolLit
    ;

// Identifiers
fullIdent
    : ident ( DOT ident )*
    ;

messageName : ident ;
enumName    : ident ;
fieldName   : ident ;
oneofName   : ident ;
mapName     : ident ;
serviceName : ident ;
rpcName     : ident ;
groupName   : ident ;

ident
    : IDENTIFIER
    | REQUIRED | OPTIONAL | REPEATED | GROUP
    | DOUBLE | FLOAT | INT32 | INT64 | UINT32 | UINT64
    | SINT32 | SINT64 | FIXED32 | FIXED64 | SFIXED32 | SFIXED64
    | BOOL | STRING | BYTES
    ;

// Literals
intLit
    : DECIMAL_LIT | OCTAL_LIT | HEX_LIT
    ;

floatLit
    : FLOAT_LIT
    ;

boolLit
    : BOOL_LIT
    ;

strLit
    : STR_LIT
    ;

emptyStatement_
    : SEMICOLON
    ;

// ====== Lexer Rules ======

// Keywords
SYNTAX      : 'syntax';
IMPORT      : 'import';
WEAK        : 'weak';
PUBLIC      : 'public';
PACKAGE     : 'package';
OPTION      : 'option';
REQUIRED    : 'required';
OPTIONAL    : 'optional';
REPEATED    : 'repeated';
ONEOF       : 'oneof';
MAP         : 'map';
RESERVED    : 'reserved';
TO          : 'to';
MAX         : 'max';
ENUM        : 'enum';
MESSAGE     : 'message';
SERVICE     : 'service';
EXTEND      : 'extend';
EXTENSIONS  : 'extensions';
GROUP       : 'group';
RPC         : 'rpc';
RETURNS     : 'returns';
STREAM      : 'stream';

// Types
DOUBLE      : 'double';
FLOAT       : 'float';
INT32       : 'int32';
INT64       : 'int64';
UINT32      : 'uint32';
UINT64      : 'uint64';
SINT32      : 'sint32';
SINT64      : 'sint64';
FIXED32     : 'fixed32';
FIXED64     : 'fixed64';
SFIXED32    : 'sfixed32';
SFIXED64    : 'sfixed64';
BOOL        : 'bool';
STRING      : 'string';
BYTES       : 'bytes';

// String literals
PROTO2      : '"proto2"' | '\'proto2\'';
PROTO3      : '"proto3"' | '\'proto3\'';

// Boolean literals
BOOL_LIT    : 'true' | 'false';

// Numeric literals
DECIMAL_LIT : ( MINUS | PLUS )? [1-9] [0-9]*;
OCTAL_LIT   : '0' [0-7]*;
HEX_LIT     : '0' [xX] HEX_DIGIT+;

FLOAT_LIT
    : ( MINUS | PLUS )? DECIMALS '.' DECIMALS? EXPONENT?
    | ( MINUS | PLUS )? DECIMALS EXPONENT
    | ( MINUS | PLUS )? '.' DECIMALS EXPONENT?
    | ( MINUS | PLUS )? 'inf'
    | ( MINUS | PLUS )? 'nan'
    ;

fragment DECIMALS   : [0-9]+;
fragment EXPONENT   : [eE] [+-]? DECIMALS;
fragment HEX_DIGIT  : [0-9a-fA-F];

// String literals
STR_LIT
    : '\'' ( CHAR_VALUE )* '\''
    | '"' ( CHAR_VALUE )* '"'
    ;

fragment CHAR_VALUE
    : HEX_ESCAPE
    | OCT_ESCAPE
    | CHAR_ESCAPE
    | ~[\u0000\n\\]
    ;

fragment HEX_ESCAPE : '\\' [xX] HEX_DIGIT HEX_DIGIT;
fragment OCT_ESCAPE : '\\' [0-3] [0-7] [0-7]
                    | '\\' [0-7] [0-7]
                    | '\\' [0-7]
                    ;
fragment CHAR_ESCAPE : '\\' [abfnrtv\\'"];

// Identifiers
IDENTIFIER  : LETTER ( LETTER | DECIMAL_DIGIT )*;
fragment LETTER         : [A-Za-z_];
fragment DECIMAL_DIGIT  : [0-9];

// Operators and punctuation
LPAREN      : '(';
RPAREN      : ')';
LBRACE      : '{';
RBRACE      : '}';
LBRACK      : '[';
RBRACK      : ']';
LANGLE      : '<';
RANGLE      : '>';
SEMICOLON   : ';';
COMMA       : ',';
DOT         : '.';
EQUAL       : '=';
MINUS       : '-';
PLUS        : '+';

// Whitespace and comments
WS          : [ \t\r\n\u000C]+ -> skip;
LINE_COMMENT: '//' ~[\r\n]* -> skip;
BLOCK_COMMENT: '/*' .*? '*/' -> skip;
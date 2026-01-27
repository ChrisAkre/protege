@Deprecated
class Test {
    static class Inner {}
}
@Deprecated
@MyAnn(Inner.class)
class Outer {
    static class Inner {}
}
@interface MyAnn { Class<?> value(); }

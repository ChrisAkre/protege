class Outer {
    @MyAnn(Inner.class)
    static class Inner {}
}
@interface MyAnn { Class<?> value(); }

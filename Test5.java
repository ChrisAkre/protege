class Outer {
    @MyAnn(Inner.Builder.class)
    static class Inner {
        static class Builder {}
    }
}
@interface MyAnn { Class<?> value(); }

@MyAnn(Outer.Inner.class)
class Outer {
    static class Inner {}
}
@interface MyAnn { Class<?> value(); }

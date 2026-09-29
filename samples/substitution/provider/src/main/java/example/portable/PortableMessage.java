package example.portable;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

@SubstituteClass("vendor.Message")
public class PortableMessage {
    public String text() {
        return "selected native provider";
    }
}

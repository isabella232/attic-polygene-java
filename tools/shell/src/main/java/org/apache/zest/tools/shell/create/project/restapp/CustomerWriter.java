package org.apache.zest.tools.shell.create.project.restapp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

public class CustomerWriter
{

    public void writeClass( Map<String, String> properties )
        throws IOException
    {
        String rootPackage = properties.get( "root.package" );
        String projectName = properties.get( "project.name" );
        try (PrintWriter pw = createPrinter( properties ))
        {
            pw.print( "package " );
            pw.print( properties.get( "root.package" ) );
            pw.println( ".model.orders;" );
            pw.println();
            pw.println("import java.time.ZonedDateTime;");
            pw.println("import org.apache.zest.api.entity.Identity;");
            pw.println("import org.apache.zest.api.property.Property;");
            pw.println();
            pw.println(
                "public interface Customer extends Identity\n" +
                "{\n" +
                "    Property<String> name();\n\n" +
                "    Property<String> address();\n\n" +
                "    Property<String> city();\n\n" +
                "    Property<String> country();\n\n" +
                "    Property<String> phone();\n\n" +
                "    Property<ZonedDateTime> registered();\n\n" +
                "}\n");
        }
    }

    private PrintWriter createPrinter( Map<String, String> properties )
        throws IOException
    {
        String packagename = properties.get( "root.package" ).replaceAll( "\\.", "/" ) + "/model/orders/";
        String classname = "Customer";
        File projectDir = new File( properties.get( "project.dir" ) );
        return new PrintWriter( new FileWriter( new File( projectDir, "model/src/main/java/" + packagename + classname + ".java" ) ) );
    }
}

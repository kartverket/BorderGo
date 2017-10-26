package no.kartverket.data.model;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.NamespaceList;
import org.simpleframework.xml.Root;


import java.util.ArrayList;
import java.util.List;

/**
 *
 * Created by Kristian on 15.05.2017.
 */
@Root(name = "FeatureCollection", strict = false)

@NamespaceList({
        @Namespace(prefix = "matrikkel", reference = "http://www.statkart.no/matrikkel "),
        @Namespace(prefix = "wfs", reference = "http://www.opengis.net/wfs"),
        @Namespace(prefix = "gml", reference = "http://www.opengis.net/gml")

})


public class WfsResponse {

    @ElementList(inline = true, name = "featureMembers")
    List<Member> members;

    public List<Member> getMembers() {
        return members;
    }


     //Represents a <code><featureMembers></featureMembers></code>-element
    @Root(name = "featureMembers", strict = false)
    public static class Member {

        @ElementList(inline = true, name = "TEIGGRENSEWFS")
        List<Boundary> boundaries;

        public List<Boundary> getBoundaries() {
            return boundaries;
        }
    }


     //Represents a <code><TEIGGRENSEWFS></TEIGGRENSEWFS></code>-element
    @Root(name = "TEIGGRENSEWFS", strict = false)
    public static class Boundary {

        @ElementList(inline=true, name = "KURVE")
        List<Curve> curves;

        public List<Curve> getCurves() {
            return curves;
        }
    }


     //Represents a <code><KURVE></KURVE></code>-element
    @Root(name = "KURVE", strict = false)
    public static class Curve {

        @ElementList(inline=true, name = "LineString")
        List<LineString> lineStrings;

        public List<LineString> getLineStrings() {
            return lineStrings;
        }
    }

     //Represents a <code><LineString></LineString></code>-element
     //Exposes the <p>posList</p> as the original string and
     //as a parsed {@link List} of {@link Double} values
    @Root(name = "LineString", strict = false)
    public static class LineString {

        @Element(name = "posList", type = String.class)
        String posList;

        public String getPosList() {
            return posList;
        }

        public List<Double> getParsedPosList() {
            String[] split = posList.split(" ");
            List<Double> positions = new ArrayList<>();
            for (String s : split) {
                //some elements might be empty
                if (s.isEmpty()) continue;
                try {
                    double d = Double.parseDouble(s);
                    positions.add(d);
                } catch (NumberFormatException e) {
                    //not a number
                }
            }
            return positions;
        }
    }
}


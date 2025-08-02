package edu.northeastern.group2_project;

import java.util.List;

public class FeatureCollection {
    public List<Feature> features;

    public static class Feature {
        public Geometry geometry;      // [lon, lat]
        public Properties properties;  // name, formatted, lat, lon
    }
    public static class Geometry { public List<Double> coordinates; }
    public static class Properties {
        public String name;
        public String formatted;
        public Double lat, lon;
    }
}

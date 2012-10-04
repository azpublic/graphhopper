/*
 *  Copyright 2012 Peter Karich 
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.util.shapes;

/**
 * A simple bounding box defined as follows: minLon, maxLon followed by minLat which is south(!) and
 * maxLat. Equally to EX_GeographicBoundingBox in the ISO 19115 standard see
 * http://osgeo-org.1560.n6.nabble.com/Boundingbox-issue-for-discussion-td3875533.html
 *
 * Nice German overview:
 * http://www.geoinf.uni-jena.de/fileadmin/Geoinformatik/Lehre/Diplomarbeiten/DA_Andres.pdf
 *
 * @author Peter Karich
 */
public class BBox implements Shape {

    public static final BBox INVERSE = new BBox();

    static {
        INVERSE.minLon = Double.MAX_VALUE;
        INVERSE.maxLon = Double.MIN_VALUE;
        INVERSE.minLat = Double.MAX_VALUE;
        INVERSE.maxLat = Double.MIN_VALUE;
    }
    // longitude (theta) = x, latitude (phi) = y
    public double minLon;
    public double maxLon;
    public double minLat;
    public double maxLat;

    private BBox() {
    }

    public BBox(double minLon, double maxLon, double minLat, double maxLat) {
        this.maxLat = maxLat;
        this.minLon = minLon;
        this.minLat = minLat;
        this.maxLon = maxLon;
    }

    public boolean check() {
        // "second longitude should be bigger than the first";
        if (minLon >= maxLon)
            return false;

        //"second latitude should be smaller than the first";
        if (minLat >= maxLat)
            return false;
        return true;

    }

    public static BBox createEarthMax() {
        return new BBox(-180.0, 180.0, -90.0, 90.0);
    }

    @Override
    public BBox clone() {
        return new BBox(minLon, maxLon, minLat, maxLat);
    }

    @Override
    public boolean intersect(Shape s) {
        if (s instanceof BBox)
            return intersect((BBox) s);
        else if (s instanceof Circle)
            return ((Circle) s).intersect(this);

        throw new UnsupportedOperationException("unsupported shape");
    }

    @Override
    public boolean contains(Shape s) {
        if (s instanceof BBox)
            return contains((BBox) s);
        else if (s instanceof Circle)
            return contains((Circle) s);

        throw new UnsupportedOperationException("unsupported shape");
    }

    public boolean intersect(Circle s) {
        return ((Circle) s).intersect(this);
    }

    public boolean intersect(BBox o) {
        // return (o.minLon < minLon && o.maxLon > minLon || o.minLon < maxLon && o.minLon >= minLon)
        //  && (o.maxLat < maxLat && o.maxLat >= minLat || o.maxLat >= maxLat && o.minLat < maxLat);
        return minLon < o.maxLon && minLat < o.maxLat && o.minLon < maxLon && o.minLat < maxLat;
    }

    @Override
    public boolean contains(double lat, double lon) {
        return lat < maxLat && lat >= minLat && lon < maxLon && lon >= minLon;
    }

    public boolean contains(BBox b) {
        return maxLat >= b.maxLat && minLat <= b.minLat && maxLon >= b.maxLon && minLon <= b.minLon;
    }

    public boolean contains(Circle c) {
        return contains(c.getBBox());
    }

    @Override
    public String toString() {
        return minLon + "," + maxLon + "," + minLat + "," + maxLat;
    }

    public String toLessPrecisionString() {
        return (float) minLon + "," + (float) maxLon + "," + (float) minLat + "," + (float) maxLat;
    }

    @Override
    public BBox getBBox() {
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        BBox b = (BBox) obj;
        return Double.doubleToLongBits(minLat) == Double.doubleToLongBits(b.minLat)
                && Double.doubleToLongBits(maxLat) == Double.doubleToLongBits(b.maxLat)
                && Double.doubleToLongBits(minLon) == Double.doubleToLongBits(b.minLon)
                && Double.doubleToLongBits(maxLon) == Double.doubleToLongBits(b.maxLon);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 17 * hash + (int) (Double.doubleToLongBits(this.minLon) ^ (Double.doubleToLongBits(this.minLon) >>> 32));
        hash = 17 * hash + (int) (Double.doubleToLongBits(this.maxLon) ^ (Double.doubleToLongBits(this.maxLon) >>> 32));
        hash = 17 * hash + (int) (Double.doubleToLongBits(this.minLat) ^ (Double.doubleToLongBits(this.minLat) >>> 32));
        hash = 17 * hash + (int) (Double.doubleToLongBits(this.maxLat) ^ (Double.doubleToLongBits(this.maxLat) >>> 32));
        return hash;
    }
}
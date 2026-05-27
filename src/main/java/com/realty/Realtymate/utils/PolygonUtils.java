package com.realty.Realtymate.utils;

import com.realty.Realtymate.model.BoundingBox;
import org.locationtech.jts.geom.*;

public class PolygonUtils {

    public static BoundingBox getBoundingBox(Polygon polygon) {
        Coordinate[] coordinates = polygon.getCoordinates();

        double minLat = Double.MAX_VALUE, maxLat = Double.MIN_VALUE;
        double minLng = Double.MAX_VALUE, maxLng = Double.MIN_VALUE;

        for (Coordinate coord : coordinates) {
            minLat = Math.min(minLat, coord.y);
            maxLat = Math.max(maxLat, coord.y);
            minLng = Math.min(minLng, coord.x);
            maxLng = Math.max(maxLng, coord.x);
        }

        return new BoundingBox(minLat, maxLat, minLng, maxLng);
    }

    public static boolean isInsidePolygon(Polygon polygon, double lng, double lat){
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

        // 좌표를 Point 객체로 변환
        Point point = geometryFactory.createPoint(new Coordinate(lng, lat));
        // 포함 여부 체크
        return polygon.contains(point);

    }
}
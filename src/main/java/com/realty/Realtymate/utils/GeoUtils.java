package com.realty.Realtymate.utils;

import org.locationtech.proj4j.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 좌표 변환 및 WKT(Well-Known Text) 생성 유틸리티
 * - WGS84 (EPSG:4326) ↔ EPSG:5181 (Korea 2000 / Central Belt 2010) 변환
 * - 반경 기반 원 → 폴리곤 변환
 * - WKT 형식 생성
 */
public class GeoUtils {

    private static final CRSFactory CRS_FACTORY = new CRSFactory();
    private static final CoordinateTransformFactory CTF = new CoordinateTransformFactory();

    // EPSG:4326 (WGS84)
    private static final CoordinateReferenceSystem WGS84 = CRS_FACTORY.createFromName("EPSG:4326");

    // EPSG:5181 (Korea 2000 / Central Belt 2010)
    // Proj4 정의: https://epsg.io/5181
    private static final String EPSG_5181_DEF =
        "+proj=tmerc +lat_0=38 +lon_0=127 +k=1 +x_0=200000 +y_0=500000 " +
        "+ellps=GRS80 +units=m +no_defs";

    private static final CoordinateReferenceSystem EPSG_5181 =
        CRS_FACTORY.createFromParameters("EPSG:5181", EPSG_5181_DEF);

    // 변환기 (WGS84 → EPSG:5181)
    private static final CoordinateTransform TO_5181 = CTF.createTransform(WGS84, EPSG_5181);

    // 변환기 (EPSG:5181 → WGS84)
    private static final CoordinateTransform FROM_5181 = CTF.createTransform(EPSG_5181, WGS84);

    /**
     * WGS84 좌표를 EPSG:5181로 변환
     * @param lat 위도 (WGS84)
     * @param lng 경도 (WGS84)
     * @return [x, y] (EPSG:5181)
     */
    public static double[] convertWgs84ToEpsg5181(double lat, double lng) {
        ProjCoordinate srcCoord = new ProjCoordinate(lng, lat); // 주의: lng, lat 순서
        ProjCoordinate dstCoord = new ProjCoordinate();

        TO_5181.transform(srcCoord, dstCoord);

        return new double[]{dstCoord.x, dstCoord.y};
    }

    /**
     * WGS84 좌표를 EPSG:5181로 변환 (별칭 - CommercialAnalysisService에서 사용)
     */
    public static double[] transformWgs84ToEpsg5181(double lat, double lng) {
        return convertWgs84ToEpsg5181(lat, lng);
    }

    /**
     * EPSG:5181 좌표를 WGS84로 변환
     * @param x X 좌표 (EPSG:5181)
     * @param y Y 좌표 (EPSG:5181)
     * @return [lat, lng] (WGS84)
     */
    public static double[] transformEpsg5181ToWgs84(double x, double y) {
        ProjCoordinate srcCoord = new ProjCoordinate(x, y);
        ProjCoordinate dstCoord = new ProjCoordinate();

        FROM_5181.transform(srcCoord, dstCoord);

        return new double[]{dstCoord.y, dstCoord.x}; // 주의: lat, lng 순서로 반환
    }

    /**
     * 반경 기반 원을 폴리곤으로 변환하여 WKT 형식으로 반환 (EPSG:5181 좌표계)
     * @param centerLat 중심점 위도 (WGS84)
     * @param centerLng 중심점 경도 (WGS84)
     * @param radiusMeters 반경 (미터)
     * @param numPoints 폴리곤 점 개수 (기본 45)
     * @return WKT POLYGON 문자열
     */
    public static String createCirclePolygonWkt(double centerLat, double centerLng,
                                                 double radiusMeters, int numPoints) {
        // 중심점을 EPSG:5181로 변환
        double[] center = convertWgs84ToEpsg5181(centerLat, centerLng);
        double centerX = center[0];
        double centerY = center[1];

        // 원을 다각형으로 근사
        List<String> points = new ArrayList<>();
        for (int i = 0; i <= numPoints; i++) { // +1로 폴리곤 닫기
            double angle = Math.toRadians(360.0 * i / numPoints);
            double x = centerX + radiusMeters * Math.cos(angle);
            double y = centerY + radiusMeters * Math.sin(angle);
            points.add(String.format("%.2f %.2f", x, y));
        }

        return String.format("POLYGON((%s))", String.join(",", points));
    }

    /**
     * 반경 기반 원을 폴리곤으로 변환 (점 개수 기본값 45)
     */
    public static String createCirclePolygonWkt(double centerLat, double centerLng,
                                                 double radiusMeters) {
        return createCirclePolygonWkt(centerLat, centerLng, radiusMeters, 45);
    }

    /**
     * 폴리곤 좌표 리스트를 WKT 형식으로 변환 (WGS84 → EPSG:5181)
     * @param coords [[lat1, lng1], [lat2, lng2], ...]
     * @return WKT POLYGON 문자열
     */
    public static String createPolygonWkt(List<double[]> coords) {
        List<String> convertedCoords = new ArrayList<>();

        for (double[] coord : coords) {
            double lat = coord[0];
            double lng = coord[1];
            double[] transformed = convertWgs84ToEpsg5181(lat, lng);
            convertedCoords.add(String.format("%.2f %.2f", transformed[0], transformed[1]));
        }

        // 첫 점을 마지막에 추가하여 폴리곤 닫기
        if (!convertedCoords.isEmpty() &&
            !convertedCoords.get(0).equals(convertedCoords.get(convertedCoords.size() - 1))) {
            convertedCoords.add(convertedCoords.get(0));
        }

        return String.format("POLYGON((%s))", String.join(",", convertedCoords));
    }

    /**
     * 폴리곤 면적 계산 (EPSG:5181 좌표계에서 제곱미터)
     * Shoelace formula 사용
     * @param coords [[lat1, lng1], [lat2, lng2], ...]
     * @return 면적 (제곱미터)
     */
    public static double calculatePolygonArea(List<double[]> coords) {
        if (coords.size() < 3) {
            return 0.0;
        }

        // 좌표를 EPSG:5181로 변환
        List<double[]> transformedCoords = new ArrayList<>();
        for (double[] coord : coords) {
            double lat = coord[0];
            double lng = coord[1];
            transformedCoords.add(convertWgs84ToEpsg5181(lat, lng));
        }

        // Shoelace formula
        double area = 0.0;
        int n = transformedCoords.size();

        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double[] p1 = transformedCoords.get(i);
            double[] p2 = transformedCoords.get(j);
            area += p1[0] * p2[1];
            area -= p2[0] * p1[1];
        }

        return Math.abs(area) / 2.0;
    }

    /**
     * 원의 면적 계산 (제곱미터)
     * @param radiusMeters 반경 (미터)
     * @return 면적 (제곱미터)
     */
    public static double calculateCircleArea(double radiusMeters) {
        return Math.PI * radiusMeters * radiusMeters;
    }
}

#! /usr/bin/env groovy
import groovy.yaml.YamlSlurper

import java.time.*
import java.util.stream.Collectors


enum VehicleType {
    MOTORBIKE("Motorbike"),
    TRACTOR("Tractor"),
    EMERGENCY("Emergency"),
    DIPLOMAT("Diplomat"),
    FOREIGN("Foreign"),
    MILITARY("Military"),
    CAR("Car");
    private final String type

    VehicleType(String type) {
        this.type = type
    }

    boolean isTullFree() {
        return this != CAR;
    }
}

class TullCalc {
    Closure helgdagarClosure = { String filename ->
        return new YamlSlurper().parseText(getClass().getResource(filename).text).collect { p ->
            LocalDate.parse(p.date)
        }
    }.memoize()

    Closure taxaClosure = { String filename ->
        return new YamlSlurper().parseText(getClass().getResource(filename).text)
    }.memoize()

    Closure dateTimeFromIso = { String s ->
        // ISO 8601
        def ldt = LocalDateTime.parse(s)
        Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * Calculate the total toll fee for one day
     *
     * @param vehicle - the vehicle
     * @param dates - date and time of all passes on one day
     * @return - the total toll fee for that day
     */
    int getTollFee(VehicleType vehicle, Date... dates) {
        if (vehicle.isTullFree()) {
            return 0
        }

        // Filtrera bort helgdagar
        def holidays = helgdagarClosure.call("helgdagar.yml")
        ArrayList chargeDays = Arrays.stream(dates)
                .map(c -> c.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .filter(d -> d.getDayOfWeek() != DayOfWeek.SATURDAY
                        || d.getDayOfWeek() != DayOfWeek.SATURDAY)
                .filter(d -> !holidays.contains(d.toLocalDate()))
                .collect(Collectors.toList())

        // Summera taxa
        return chargeDays.stream()
                .map(m -> {
                    def charge = taxaClosure.call("taxa.yml").stream()
                            .filter(t -> {
                                LocalTime inT = LocalTime.parse(t.start)
                                LocalTime outT = LocalTime.parse(t.stop)
                                LocalTime actT = m.toLocalTime()
                                // println "--> $actT -> $inT till $outT"
                                actT.is(inT) || (actT.isAfter(inT) && actT.isBefore(outT))
                            })//.peek(System.out::println)
                            .map(c -> {
                                (Double.parseDouble(c.charge)).intValue()
                            })
                            .findFirst()
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .mapToInt(i -> i).sum();
    }

    def run() {
        def carDates = []
        carDates << dateTimeFromIso("2021-06-19T09:50:55")
        carDates << dateTimeFromIso("2021-07-12T17:20:55")
        carDates << dateTimeFromIso("2021-12-20T11:50:55")
        carDates << dateTimeFromIso("2021-12-31T12:50:55")
        carDates << dateTimeFromIso("2021-12-24T13:50:55")

        println "Taxa for $VehicleType.CAR: ${getTollFee(VehicleType.CAR, carDates as Date[])}"

        def diplomatDates = []
        diplomatDates << dateTimeFromIso("2021-06-19T09:50:55")
        diplomatDates << dateTimeFromIso("2021-07-12T17:20:55")

        println "Taxa for $VehicleType.DIPLOMAT: ${getTollFee(VehicleType.DIPLOMAT, carDates as Date[])}"

    }

    static void main(String[] args) {
        TullCalc tc = new TullCalc()
        tc.run()
    }
}


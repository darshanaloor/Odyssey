package com.odyssey.service;

import com.odyssey.entity.*;
import com.odyssey.repository.jpa.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Order(10)
public class MarketplaceSeeder implements CommandLineRunner {

    private final OperatorJpaRepository operatorRepo;
    private final ServiceJpaRepository serviceRepo;
    private final ServiceDepartureJpaRepository departureRepo;
    private final HotelJpaRepository hotelRepo;
    private final CabServiceJpaRepository cabRepo;

    @Override
    public void run(String... args) {
        seedMarketplace();
    }

    @Transactional
    public synchronized void seedMarketplace() {
        log.info("Starting deterministic marketplace seeder...");

        // 1. Operators
        Map<String, OperatorEntity> operators = seedOperators();

        // 2. Transport Services
        List<ServiceEntity> services = seedServices(operators);

        // 3. Service Departures for next 14 days
        seedDepartures(services);

        // 4. Hotels (at least 3 per city)
        seedHotels();

        // 5. Cabs (at least 2 per city)
        seedCabs();

        log.info("Marketplace catalogue successfully seeded and verified.");
    }

    private Map<String, OperatorEntity> seedOperators() {
        Map<String, OperatorEntity> map = new HashMap<>();

        saveOperator(map, "OP-KSRTC", "KSRTC", "BUS", "support@ksrtc.in");
        saveOperator(map, "OP-VRL", "VRL Travels", "BUS", "helpdesk@vrl.in");
        saveOperator(map, "OP-KADAMBA", "Kadamba Transport", "BUS", "customercare@ktcl.goa.gov.in");
        saveOperator(map, "OP-IRCTC", "Indian Railways", "TRAIN", "customercare@irctc.co.in");
        saveOperator(map, "OP-INDIGO", "IndiGo", "FLIGHT", "customer.experience@goindigo.in");
        saveOperator(map, "OP-AIRINDIA", "Air India", "FLIGHT", "contactus@airindia.com");
        saveOperator(map, "OP-STAR", "Star Air", "FLIGHT", "support@starair.in");
        saveOperator(map, "OP-OLA", "Ola Cabs", "CAB", "support@olacabs.com");
        saveOperator(map, "OP-UBER", "Uber India", "CAB", "support@uber.com");
        saveOperator(map, "OP-GEN-BUS", "State Transport Express", "BUS", "+91-1800-000-001");
        saveOperator(map, "OP-GEN-TRAIN", "Intercity Rail Service", "TRAIN", "+91-1800-000-002");
        saveOperator(map, "OP-GEN-FLIGHT", "Domestic Air Service", "FLIGHT", "+91-1800-000-003");

        return map;
    }

    private void saveOperator(Map<String, OperatorEntity> map, String id, String name, String mode, String contact) {
        OperatorEntity op = operatorRepo.findById(id).orElseGet(() ->
                operatorRepo.save(OperatorEntity.builder()
                        .id(id)
                        .name(name)
                        .mode(mode)
                        .contact(contact)
                        .build())
        );
        map.put(id, op);
    }

    private List<ServiceEntity> seedServices(Map<String, OperatorEntity> ops) {
        List<ServiceEntity> list = new ArrayList<>();

        // Pair 1: HUBBALLI <-> GOA (both directions)
        addService(list, "SRV-HBX-GOA-BS-1", ops.get("OP-KSRTC"), "BUS", "KA-25-F-1101",
                "HUBBALLI", "GOA", "Old Bus Stand, Hubballi", "Kadamba Bus Stand, Panaji",
                LocalTime.of(6, 30), LocalTime.of(11, 30), false, new BigDecimal("450.00"), 40, 88);
        addService(list, "SRV-HBX-GOA-BS-2", ops.get("OP-VRL"), "BUS", "KA-25-F-2202",
                "HUBBALLI", "GOA", "Gokul Road Terminal, Hubballi", "Kadamba Bus Stand, Panaji",
                LocalTime.of(14, 0), LocalTime.of(19, 15), false, new BigDecimal("520.00"), 36, 90);
        addService(list, "SRV-HBX-GOA-TR-1", ops.get("OP-IRCTC"), "TRAIN", "12779 Goa Exp",
                "HUBBALLI", "GOA", "Hubballi Junction (UBL)", "Madgaon Junction (MAO)",
                LocalTime.of(8, 15), LocalTime.of(12, 45), false, new BigDecimal("280.00"), 72, 94);
        addService(list, "SRV-HBX-GOA-TR-2", ops.get("OP-IRCTC"), "TRAIN", "16589 Vasco Intercity",
                "HUBBALLI", "GOA", "Hubballi Junction (UBL)", "Vasco Da Gama Station (VSG)",
                LocalTime.of(16, 30), LocalTime.of(21, 0), false, new BigDecimal("310.00"), 72, 92);

        // Reverse: GOA -> HUBBALLI
        addService(list, "SRV-GOA-HBX-BS-1", ops.get("OP-KSRTC"), "BUS", "KA-25-F-1102",
                "GOA", "HUBBALLI", "Kadamba Bus Stand, Panaji", "Old Bus Stand, Hubballi",
                LocalTime.of(7, 0), LocalTime.of(12, 0), false, new BigDecimal("450.00"), 40, 88);
        addService(list, "SRV-GOA-HBX-BS-2", ops.get("OP-VRL"), "BUS", "KA-25-F-2203",
                "GOA", "HUBBALLI", "Kadamba Bus Stand, Panaji", "Gokul Road Terminal, Hubballi",
                LocalTime.of(15, 30), LocalTime.of(20, 45), false, new BigDecimal("520.00"), 36, 90);
        addService(list, "SRV-GOA-HBX-TR-1", ops.get("OP-IRCTC"), "TRAIN", "12780 Nizamuddin Exp",
                "GOA", "HUBBALLI", "Madgaon Junction (MAO)", "Hubballi Junction (UBL)",
                LocalTime.of(9, 0), LocalTime.of(13, 30), false, new BigDecimal("280.00"), 72, 94);
        addService(list, "SRV-GOA-HBX-TR-2", ops.get("OP-IRCTC"), "TRAIN", "16590 Rani Chennamma",
                "GOA", "HUBBALLI", "Vasco Da Gama Station (VSG)", "Hubballi Junction (UBL)",
                LocalTime.of(17, 15), LocalTime.of(21, 45), false, new BigDecimal("310.00"), 72, 92);

        // Pair 2: BENGALURU <-> MANGALURU
        addService(list, "SRV-BLR-IXE-FL-1", ops.get("OP-INDIGO"), "FLIGHT", "6E-6512",
                "BENGALURU", "MANGALURU", "BLR (Kempegowda Int'l Airport)", "IXE (Mangaluru Int'l Airport)",
                LocalTime.of(13, 15), LocalTime.of(14, 25), false, new BigDecimal("6200.00"), 180, 92);
        addService(list, "SRV-BLR-IXE-FL-2", ops.get("OP-AIRINDIA"), "FLIGHT", "AI-582",
                "BENGALURU", "MANGALURU", "BLR (Kempegowda Int'l Airport)", "IXE (Mangaluru Int'l Airport)",
                LocalTime.of(8, 30), LocalTime.of(9, 40), false, new BigDecimal("5800.00"), 150, 90);
        addService(list, "SRV-BLR-IXE-BS-1", ops.get("OP-KSRTC"), "BUS", "KA-01-F-4001",
                "BENGALURU", "MANGALURU", "Kempegowda Bus Station (Majestic)", "KSRTC Bus Stand (Bejai)",
                LocalTime.of(10, 45), LocalTime.of(17, 15), false, new BigDecimal("1250.00"), 44, 86);
        addService(list, "SRV-BLR-IXE-TR-1", ops.get("OP-IRCTC"), "TRAIN", "16595 Panchaganga",
                "BENGALURU", "MANGALURU", "KSR Bengaluru (SBC)", "Mangaluru Central (MAQ)",
                LocalTime.of(18, 50), LocalTime.of(5, 30), true, new BigDecimal("1450.00"), 72, 95);

        // Reverse: MANGALURU -> BENGALURU
        addService(list, "SRV-IXE-BLR-FL-1", ops.get("OP-INDIGO"), "FLIGHT", "6E-6513",
                "MANGALURU", "BENGALURU", "IXE (Mangaluru Int'l Airport)", "BLR (Kempegowda Int'l Airport)",
                LocalTime.of(15, 0), LocalTime.of(16, 10), false, new BigDecimal("6200.00"), 180, 92);
        addService(list, "SRV-IXE-BLR-BS-1", ops.get("OP-KSRTC"), "BUS", "KA-01-F-4002",
                "MANGALURU", "BENGALURU", "KSRTC Bus Stand (Bejai)", "Kempegowda Bus Station (Majestic)",
                LocalTime.of(11, 0), LocalTime.of(17, 30), false, new BigDecimal("1250.00"), 44, 86);
        addService(list, "SRV-IXE-BLR-TR-1", ops.get("OP-IRCTC"), "TRAIN", "16596 Panchaganga",
                "MANGALURU", "BENGALURU", "Mangaluru Central (MAQ)", "KSR Bengaluru (SBC)",
                LocalTime.of(21, 40), LocalTime.of(7, 15), true, new BigDecimal("1450.00"), 72, 95);

        // Pair 3: BENGALURU <-> HUBBALLI
        addService(list, "SRV-BLR-HBX-TR-1", ops.get("OP-IRCTC"), "TRAIN", "20661 Vande Bharat",
                "BENGALURU", "HUBBALLI", "KSR Bengaluru (SBC)", "Hubballi Junction (UBL)",
                LocalTime.of(5, 45), LocalTime.of(10, 40), false, new BigDecimal("1220.00"), 80, 96);
        addService(list, "SRV-BLR-HBX-BS-1", ops.get("OP-KSRTC"), "BUS", "KA-01-F-3301",
                "BENGALURU", "HUBBALLI", "Kempegowda Bus Station (Majestic)", "New Bus Stand, Hubballi",
                LocalTime.of(8, 0), LocalTime.of(14, 30), false, new BigDecimal("780.00"), 40, 89);
        addService(list, "SRV-BLR-HBX-FL-1", ops.get("OP-STAR"), "FLIGHT", "S9-101",
                "BENGALURU", "HUBBALLI", "BLR (Kempegowda Int'l Airport)", "HBX (Hubballi Airport)",
                LocalTime.of(11, 15), LocalTime.of(12, 15), false, new BigDecimal("3900.00"), 50, 93);

        // Reverse: HUBBALLI -> BENGALURU
        addService(list, "SRV-HBX-BLR-TR-1", ops.get("OP-IRCTC"), "TRAIN", "20662 Vande Bharat",
                "HUBBALLI", "BENGALURU", "Hubballi Junction (UBL)", "KSR Bengaluru (SBC)",
                LocalTime.of(14, 0), LocalTime.of(18, 55), false, new BigDecimal("1220.00"), 80, 96);
        addService(list, "SRV-HBX-BLR-BS-1", ops.get("OP-KSRTC"), "BUS", "KA-01-F-3302",
                "HUBBALLI", "BENGALURU", "New Bus Stand, Hubballi", "Kempegowda Bus Station (Majestic)",
                LocalTime.of(9, 30), LocalTime.of(16, 0), false, new BigDecimal("780.00"), 40, 89);
        addService(list, "SRV-HBX-BLR-FL-1", ops.get("OP-STAR"), "FLIGHT", "S9-102",
                "HUBBALLI", "BENGALURU", "HBX (Hubballi Airport)", "BLR (Kempegowda Int'l Airport)",
                LocalTime.of(13, 0), LocalTime.of(14, 0), false, new BigDecimal("3900.00"), 50, 93);

        // Pair 4: BENGALURU <-> MYSURU
        addService(list, "SRV-BLR-MYS-TR-1", ops.get("OP-IRCTC"), "TRAIN", "20607 Vande Bharat",
                "BENGALURU", "MYSURU", "KSR Bengaluru (SBC)", "Mysuru Junction (MYS)",
                LocalTime.of(10, 20), LocalTime.of(12, 0), false, new BigDecimal("550.00"), 80, 98);
        addService(list, "SRV-BLR-MYS-BS-1", ops.get("OP-KSRTC"), "BUS", "KA-09-F-5501",
                "BENGALURU", "MYSURU", "Satellite Bus Stand, Bengaluru", "Suburban Bus Stand, Mysuru",
                LocalTime.of(7, 30), LocalTime.of(10, 0), false, new BigDecimal("320.00"), 45, 91);
        addService(list, "SRV-BLR-MYS-TR-2", ops.get("OP-IRCTC"), "TRAIN", "12007 Shatabdi",
                "BENGALURU", "MYSURU", "KSR Bengaluru (SBC)", "Mysuru Junction (MYS)",
                LocalTime.of(11, 0), LocalTime.of(13, 0), false, new BigDecimal("490.00"), 75, 96);

        // Reverse: MYSURU -> BENGALURU
        addService(list, "SRV-MYS-BLR-TR-1", ops.get("OP-IRCTC"), "TRAIN", "20608 Vande Bharat",
                "MYSURU", "BENGALURU", "Mysuru Junction (MYS)", "KSR Bengaluru (SBC)",
                LocalTime.of(13, 10), LocalTime.of(14, 50), false, new BigDecimal("550.00"), 80, 98);
        addService(list, "SRV-MYS-BLR-BS-1", ops.get("OP-KSRTC"), "BUS", "KA-09-F-5502",
                "MYSURU", "BENGALURU", "Suburban Bus Stand, Mysuru", "Satellite Bus Stand, Bengaluru",
                LocalTime.of(8, 0), LocalTime.of(10, 30), false, new BigDecimal("320.00"), 45, 91);
        addService(list, "SRV-MYS-BLR-TR-2", ops.get("OP-IRCTC"), "TRAIN", "12008 Shatabdi",
                "MYSURU", "BENGALURU", "Mysuru Junction (MYS)", "KSR Bengaluru (SBC)",
                LocalTime.of(14, 15), LocalTime.of(16, 15), false, new BigDecimal("490.00"), 75, 96);

        // Pair 5: MANGALURU <-> UDUPI
        addService(list, "SRV-IXE-UD-BS-1", ops.get("OP-KSRTC"), "BUS", "KA-19-F-7701",
                "MANGALURU", "UDUPI", "KSRTC Bus Stand, Bejai", "City Bus Stand, Udupi",
                LocalTime.of(7, 30), LocalTime.of(8, 45), false, new BigDecimal("120.00"), 45, 94);
        addService(list, "SRV-IXE-UD-BS-2", ops.get("OP-KSRTC"), "BUS", "KA-19-F-7702",
                "MANGALURU", "UDUPI", "State Bank Terminal, Mangaluru", "Service Bus Stand, Udupi",
                LocalTime.of(12, 0), LocalTime.of(13, 15), false, new BigDecimal("120.00"), 45, 94);
        addService(list, "SRV-IXE-UD-TR-1", ops.get("OP-IRCTC"), "TRAIN", "16515 Karwar Exp",
                "MANGALURU", "UDUPI", "Mangaluru Junction (MAJN)", "Udupi Railway Station (UD)",
                LocalTime.of(16, 45), LocalTime.of(17, 35), false, new BigDecimal("85.00"), 72, 92);

        // Reverse: UDUPI -> MANGALURU
        addService(list, "SRV-UD-IXE-BS-1", ops.get("OP-KSRTC"), "BUS", "KA-19-F-7703",
                "UDUPI", "MANGALURU", "Service Bus Stand, Udupi", "KSRTC Bus Stand, Bejai",
                LocalTime.of(9, 15), LocalTime.of(10, 30), false, new BigDecimal("120.00"), 45, 94);
        addService(list, "SRV-UD-IXE-BS-2", ops.get("OP-KSRTC"), "BUS", "KA-19-F-7704",
                "UDUPI", "MANGALURU", "City Bus Stand, Udupi", "State Bank Terminal, Mangaluru",
                LocalTime.of(14, 0), LocalTime.of(15, 15), false, new BigDecimal("120.00"), 45, 94);
        addService(list, "SRV-UD-IXE-TR-1", ops.get("OP-IRCTC"), "TRAIN", "16516 Yesvantpur Exp",
                "UDUPI", "MANGALURU", "Udupi Railway Station (UD)", "Mangaluru Junction (MAJN)",
                LocalTime.of(11, 20), LocalTime.of(12, 10), false, new BigDecimal("85.00"), 72, 92);

        // Pair 6: MANGALURU <-> GOA
        addService(list, "SRV-IXE-GOA-TR-1", ops.get("OP-IRCTC"), "TRAIN", "22636 Madgaon Vande Bharat",
                "MANGALURU", "GOA", "Mangaluru Central (MAQ)", "Madgaon Junction (MAO)",
                LocalTime.of(8, 30), LocalTime.of(13, 15), false, new BigDecimal("1150.00"), 75, 95);
        addService(list, "SRV-IXE-GOA-BS-1", ops.get("OP-KSRTC"), "BUS", "KA-19-F-8801",
                "MANGALURU", "GOA", "KSRTC Bus Stand, Bejai", "Kadamba Bus Stand, Panaji",
                LocalTime.of(6, 0), LocalTime.of(13, 0), false, new BigDecimal("680.00"), 40, 89);
        addService(list, "SRV-IXE-GOA-BS-2", ops.get("OP-VRL"), "BUS", "KA-19-F-8802",
                "MANGALURU", "GOA", "Pumpwell Circle, Mangaluru", "Kadamba Bus Stand, Panaji",
                LocalTime.of(21, 30), LocalTime.of(5, 0), true, new BigDecimal("850.00"), 36, 88);

        // Reverse: GOA -> MANGALURU
        addService(list, "SRV-GOA-IXE-TR-1", ops.get("OP-IRCTC"), "TRAIN", "22635 Mangaluru Vande Bharat",
                "GOA", "MANGALURU", "Madgaon Junction (MAO)", "Mangaluru Central (MAQ)",
                LocalTime.of(14, 30), LocalTime.of(19, 15), false, new BigDecimal("1150.00"), 75, 95);
        addService(list, "SRV-GOA-IXE-BS-1", ops.get("OP-KSRTC"), "BUS", "KA-19-F-8803",
                "GOA", "MANGALURU", "Kadamba Bus Stand, Panaji", "KSRTC Bus Stand, Bejai",
                LocalTime.of(8, 0), LocalTime.of(15, 0), false, new BigDecimal("680.00"), 40, 89);
        addService(list, "SRV-GOA-IXE-BS-2", ops.get("OP-VRL"), "BUS", "KA-19-F-8804",
                "GOA", "MANGALURU", "Kadamba Bus Stand, Panaji", "Pumpwell Circle, Mangaluru",
                LocalTime.of(22, 0), LocalTime.of(5, 30), true, new BigDecimal("850.00"), 36, 88);

        // Pair 7: GOA <-> MUMBAI
        addService(list, "SRV-GOA-BOM-FL-1", ops.get("OP-INDIGO"), "FLIGHT", "6E-514",
                "GOA", "MUMBAI", "GOI (Dabolim Airport)", "BOM (Chhatrapati Shivaji Maharaj Int'l)",
                LocalTime.of(9, 45), LocalTime.of(11, 0), false, new BigDecimal("4200.00"), 180, 93);
        addService(list, "SRV-GOA-BOM-TR-1", ops.get("OP-IRCTC"), "TRAIN", "22230 Vande Bharat",
                "GOA", "MUMBAI", "Madgaon Junction (MAO)", "CSMT Mumbai",
                LocalTime.of(14, 40), LocalTime.of(22, 25), false, new BigDecimal("1850.00"), 80, 95);
        addService(list, "SRV-GOA-BOM-BS-1", ops.get("OP-KADAMBA"), "BUS", "GA-03-F-1010",
                "GOA", "MUMBAI", "Kadamba Bus Stand, Panaji", "Borivali, Mumbai",
                LocalTime.of(18, 0), LocalTime.of(7, 30), true, new BigDecimal("1100.00"), 36, 87);

        // Reverse: MUMBAI -> GOA
        addService(list, "SRV-BOM-GOA-FL-1", ops.get("OP-INDIGO"), "FLIGHT", "6E-515",
                "MUMBAI", "GOA", "BOM (Chhatrapati Shivaji Maharaj Int'l)", "GOI (Dabolim Airport)",
                LocalTime.of(12, 15), LocalTime.of(13, 30), false, new BigDecimal("4200.00"), 180, 93);
        addService(list, "SRV-BOM-GOA-TR-1", ops.get("OP-IRCTC"), "TRAIN", "22229 Vande Bharat",
                "MUMBAI", "GOA", "CSMT Mumbai", "Madgaon Junction (MAO)",
                LocalTime.of(5, 25), LocalTime.of(13, 10), false, new BigDecimal("1850.00"), 80, 95);
        addService(list, "SRV-BOM-GOA-BS-1", ops.get("OP-KADAMBA"), "BUS", "GA-03-F-1011",
                "MUMBAI", "GOA", "Borivali, Mumbai", "Kadamba Bus Stand, Panaji",
                LocalTime.of(19, 0), LocalTime.of(8, 30), true, new BigDecimal("1100.00"), 36, 87);

        // Pair 8: BENGALURU <-> CHENNAI
        addService(list, "SRV-BLR-MAA-TR-1", ops.get("OP-IRCTC"), "TRAIN", "20608 Vande Bharat",
                "BENGALURU", "CHENNAI", "KSR Bengaluru (SBC)", "Chennai Central (MAS)",
                LocalTime.of(14, 50), LocalTime.of(19, 20), false, new BigDecimal("1050.00"), 80, 97);
        addService(list, "SRV-BLR-MAA-FL-1", ops.get("OP-INDIGO"), "FLIGHT", "6E-421",
                "BENGALURU", "CHENNAI", "BLR (Kempegowda Int'l Airport)", "MAA (Chennai Int'l Airport)",
                LocalTime.of(8, 10), LocalTime.of(9, 10), false, new BigDecimal("3800.00"), 180, 94);
        addService(list, "SRV-BLR-MAA-BS-1", ops.get("OP-KSRTC"), "BUS", "KA-01-F-9901",
                "BENGALURU", "CHENNAI", "Shantinagar Bus Station", "Koyambedu Bus Stand, Chennai",
                LocalTime.of(6, 30), LocalTime.of(13, 0), false, new BigDecimal("750.00"), 42, 90);

        // Reverse: CHENNAI -> BENGALURU
        addService(list, "SRV-MAA-BLR-TR-1", ops.get("OP-IRCTC"), "TRAIN", "20607 Vande Bharat",
                "CHENNAI", "BENGALURU", "Chennai Central (MAS)", "KSR Bengaluru (SBC)",
                LocalTime.of(5, 50), LocalTime.of(10, 20), false, new BigDecimal("1050.00"), 80, 97);
        addService(list, "SRV-MAA-BLR-FL-1", ops.get("OP-INDIGO"), "FLIGHT", "6E-422",
                "CHENNAI", "BENGALURU", "MAA (Chennai Int'l Airport)", "BLR (Kempegowda Int'l Airport)",
                LocalTime.of(10, 0), LocalTime.of(11, 0), false, new BigDecimal("3800.00"), 180, 94);
        addService(list, "SRV-MAA-BLR-BS-1", ops.get("OP-KSRTC"), "BUS", "KA-01-F-9902",
                "CHENNAI", "BENGALURU", "Koyambedu Bus Stand, Chennai", "Shantinagar Bus Station",
                LocalTime.of(14, 0), LocalTime.of(20, 30), false, new BigDecimal("750.00"), 42, 90);

        // Pair 9: BENGALURU <-> HYDERABAD
        addService(list, "SRV-BLR-HYD-FL-1", ops.get("OP-INDIGO"), "FLIGHT", "6E-804",
                "BENGALURU", "HYDERABAD", "BLR (Kempegowda Int'l Airport)", "HYD (Rajiv Gandhi Int'l Airport)",
                LocalTime.of(7, 0), LocalTime.of(8, 10), false, new BigDecimal("4400.00"), 180, 93);
        addService(list, "SRV-BLR-HYD-TR-1", ops.get("OP-IRCTC"), "TRAIN", "20704 Vande Bharat",
                "BENGALURU", "HYDERABAD", "Yesvantpur Junction (YPR)", "Kacheguda (KCG)",
                LocalTime.of(14, 45), LocalTime.of(23, 0), false, new BigDecimal("1600.00"), 80, 96);
        addService(list, "SRV-BLR-HYD-BS-1", ops.get("OP-VRL"), "BUS", "KA-01-F-7711",
                "BENGALURU", "HYDERABAD", "Anand Rao Circle, Bengaluru", "MGBS Hyderabad",
                LocalTime.of(21, 30), LocalTime.of(6, 30), true, new BigDecimal("1200.00"), 36, 88);

        // Reverse: HYDERABAD -> BENGALURU
        addService(list, "SRV-HYD-BLR-FL-1", ops.get("OP-INDIGO"), "FLIGHT", "6E-805",
                "HYDERABAD", "BENGALURU", "HYD (Rajiv Gandhi Int'l Airport)", "BLR (Kempegowda Int'l Airport)",
                LocalTime.of(9, 0), LocalTime.of(10, 10), false, new BigDecimal("4400.00"), 180, 93);
        addService(list, "SRV-HYD-BLR-TR-1", ops.get("OP-IRCTC"), "TRAIN", "20703 Vande Bharat",
                "HYDERABAD", "BENGALURU", "Kacheguda (KCG)", "Yesvantpur Junction (YPR)",
                LocalTime.of(5, 30), LocalTime.of(13, 45), false, new BigDecimal("1600.00"), 80, 96);
        addService(list, "SRV-HYD-BLR-BS-1", ops.get("OP-VRL"), "BUS", "KA-01-F-7712",
                "HYDERABAD", "BENGALURU", "MGBS Hyderabad", "Anand Rao Circle, Bengaluru",
                LocalTime.of(21, 30), LocalTime.of(6, 30), true, new BigDecimal("1200.00"), 36, 88);

        // Pair 10: PUNE <-> GOA
        addService(list, "SRV-PUNE-GOA-BS-1", ops.get("OP-KADAMBA"), "BUS", "GA-01-F-3030",
                "PUNE", "GOA", "Swargate Bus Stand, Pune", "Kadamba Bus Stand, Panaji",
                LocalTime.of(20, 0), LocalTime.of(6, 0), true, new BigDecimal("950.00"), 36, 87);
        addService(list, "SRV-PUNE-GOA-TR-1", ops.get("OP-IRCTC"), "TRAIN", "12780 Goa Express",
                "PUNE", "GOA", "Pune Junction (PUNE)", "Madgaon Junction (MAO)",
                LocalTime.of(16, 20), LocalTime.of(3, 40), true, new BigDecimal("650.00"), 72, 92);
        addService(list, "SRV-PUNE-GOA-FL-1", ops.get("OP-INDIGO"), "FLIGHT", "6E-602",
                "PUNE", "GOA", "PNQ (Pune Airport)", "GOI (Dabolim Airport)",
                LocalTime.of(12, 10), LocalTime.of(13, 20), false, new BigDecimal("3900.00"), 180, 91);

        // Reverse: GOA -> PUNE
        addService(list, "SRV-GOA-PUNE-BS-1", ops.get("OP-KADAMBA"), "BUS", "GA-01-F-3031",
                "GOA", "PUNE", "Kadamba Bus Stand, Panaji", "Swargate Bus Stand, Pune",
                LocalTime.of(19, 30), LocalTime.of(5, 30), true, new BigDecimal("950.00"), 36, 87);
        addService(list, "SRV-GOA-PUNE-TR-1", ops.get("OP-IRCTC"), "TRAIN", "12779 Goa Express",
                "GOA", "PUNE", "Madgaon Junction (MAO)", "Pune Junction (PUNE)",
                LocalTime.of(15, 0), LocalTime.of(2, 15), true, new BigDecimal("650.00"), 72, 92);
        addService(list, "SRV-GOA-PUNE-FL-1", ops.get("OP-INDIGO"), "FLIGHT", "6E-603",
                "GOA", "PUNE", "GOI (Dabolim Airport)", "PNQ (Pune Airport)",
                LocalTime.of(14, 0), LocalTime.of(15, 10), false, new BigDecimal("3900.00"), 180, 91);

        // Pair 11: BENGALURU <-> GOA
        addService(list, "SRV-BLR-GOA-FL-1", ops.get("OP-INDIGO"), "FLIGHT", "6E-344",
                "BENGALURU", "GOA", "BLR (Kempegowda Int'l Airport)", "GOI (Dabolim Airport)",
                LocalTime.of(10, 30), LocalTime.of(11, 45), false, new BigDecimal("4900.00"), 180, 94);
        addService(list, "SRV-BLR-GOA-BS-1", ops.get("OP-KSRTC"), "BUS", "KA-01-F-6601",
                "BENGALURU", "GOA", "Kempegowda Bus Station (Majestic)", "Kadamba Bus Stand, Panaji",
                LocalTime.of(19, 30), LocalTime.of(7, 0), true, new BigDecimal("1350.00"), 40, 88);
        addService(list, "SRV-BLR-GOA-TR-1", ops.get("OP-IRCTC"), "TRAIN", "17309 Vasco Express",
                "BENGALURU", "GOA", "Yesvantpur (YPR)", "Madgaon Junction (MAO)",
                LocalTime.of(15, 0), LocalTime.of(4, 30), true, new BigDecimal("920.00"), 72, 91);

        // Reverse: GOA -> BENGALURU
        addService(list, "SRV-GOA-BLR-FL-1", ops.get("OP-INDIGO"), "FLIGHT", "6E-345",
                "GOA", "BENGALURU", "GOI (Dabolim Airport)", "BLR (Kempegowda Int'l Airport)",
                LocalTime.of(12, 30), LocalTime.of(13, 45), false, new BigDecimal("4900.00"), 180, 94);
        addService(list, "SRV-GOA-BLR-BS-1", ops.get("OP-KSRTC"), "BUS", "KA-01-F-6602",
                "GOA", "BENGALURU", "Kadamba Bus Stand, Panaji", "Kempegowda Bus Station (Majestic)",
                LocalTime.of(19, 0), LocalTime.of(6, 30), true, new BigDecimal("1350.00"), 40, 88);
        addService(list, "SRV-GOA-BLR-TR-1", ops.get("OP-IRCTC"), "TRAIN", "17310 Yesvantpur Express",
                "GOA", "BENGALURU", "Madgaon Junction (MAO)", "Yesvantpur (YPR)",
                LocalTime.of(22, 55), LocalTime.of(12, 30), true, new BigDecimal("920.00"), 72, 91);

        return list;
    }

    private void addService(List<ServiceEntity> list, String id, OperatorEntity op, String mode,
                            String vehicleNumber, String originCity, String destinationCity,
                            String originStop, String destinationStop, LocalTime depTime, LocalTime arrTime,
                            boolean arrivesNextDay, BigDecimal price, int seats, int reliability) {
        ServiceEntity s = serviceRepo.findById(id).orElseGet(() ->
                serviceRepo.save(ServiceEntity.builder()
                        .id(id)
                        .operator(op)
                        .mode(mode)
                        .vehicleNumber(vehicleNumber)
                        .originCity(originCity)
                        .destinationCity(destinationCity)
                        .originStop(originStop)
                        .destinationStop(destinationStop)
                        .departureTime(depTime)
                        .arrivalTime(arrTime)
                        .arrivesNextDay(arrivesNextDay)
                        .price(price)
                        .seatsTotal(seats)
                        .reliabilityScore(reliability)
                        .active(true)
                        .build())
        );
        list.add(s);
    }

    private void seedDepartures(List<ServiceEntity> services) {
        LocalDate today = LocalDate.now();
        List<ServiceDepartureEntity> toSave = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        Set<String> seenServiceDates = new HashSet<>();

        for (int d = 0; d <= 14; d++) {
            LocalDate date = today.plusDays(d);
            Set<String> existingServiceIds = new HashSet<>();
            for (ServiceDepartureEntity existing : departureRepo.findByTravelDate(date)) {
                if (existing.getService() != null && existing.getService().getId() != null) {
                    existingServiceIds.add(existing.getService().getId());
                }
            }

            for (ServiceEntity service : services) {
                if (service == null || service.getId() == null) {
                    continue;
                }
                String serviceDateKey = service.getId() + "|" + date;
                if (existingServiceIds.contains(service.getId()) || seenServiceDates.contains(serviceDateKey)) {
                    continue;
                }
                String depId = "DEP-" + service.getId() + "-" + date;
                if (seenIds.contains(depId) || departureRepo.existsById(depId)) {
                    continue;
                }
                seenIds.add(depId);
                seenServiceDates.add(serviceDateKey);
                toSave.add(ServiceDepartureEntity.builder()
                        .id(depId)
                        .service(service)
                        .travelDate(date)
                        .seatsAvailable(service.getSeatsTotal())
                        .status("SCHEDULED")
                        .delayMinutes(0)
                        .build());
            }
        }

        if (!toSave.isEmpty()) {
            departureRepo.saveAll(toSave);
            log.info("Seeded {} new daily service departures.", toSave.size());
        }
    }

    private void seedHotels() {
        // HUBBALLI
        saveHotel("HTL-HBX-4", "Clarks Inn Airport Hotel Hubli", "HUBBALLI", "Airport Road, Gokul, Hubballi", new BigDecimal("1800.00"), new BigDecimal("3.9"), 45, "stay@clarksinnairport.com");
        saveHotel("HTL-HBX-3", "Hotel Metropolis", "HUBBALLI", "Koppikar Road, Hubballi", new BigDecimal("2400.00"), new BigDecimal("4.0"), 40, "stay@hotelmetropolis.in");
        saveHotel("HTL-HBX-2", "The Den Hubballi", "HUBBALLI", "Lamington Road, Hubballi", new BigDecimal("3500.00"), new BigDecimal("4.2"), 50, "stay@thedenhotel.com");
        saveHotel("HTL-HBX-1", "Fortune Park Airport Road", "HUBBALLI", "Gokul Road, Hubballi", new BigDecimal("4200.00"), new BigDecimal("4.4"), 65, "reservations@fortunehubli.com");

        // GOA
        saveHotel("HTL-GOA-4", "Hotel Mandovi Panaji", "GOA", "D.B. Marg, Panaji, Goa", new BigDecimal("2400.00"), new BigDecimal("4.0"), 60, "reservations@hotelmandovi.com");
        saveHotel("HTL-GOA-1", "Goa Marriott Resort & Spa", "GOA", "Miramar Beach, Panaji", new BigDecimal("9500.00"), new BigDecimal("4.7"), 120, "goa.marriott@marriott.com");
        saveHotel("HTL-GOA-2", "Taj Cidade de Goa", "GOA", "Vainguinim Beach, Dona Paula", new BigDecimal("11500.00"), new BigDecimal("4.8"), 150, "cidade.goa@tajhotels.com");
        saveHotel("HTL-GOA-3", "Grand Hyatt Goa", "GOA", "Bambolim Bay, Goa", new BigDecimal("12800.00"), new BigDecimal("4.8"), 180, "goa.grand@hyatt.com");

        // BENGALURU
        saveHotel("HTL-BLR-4", "Hotel Maurya Bangalore", "BENGALURU", "Race Course Road, Bengaluru", new BigDecimal("1750.00"), new BigDecimal("3.9"), 60, "stay@hotelmauryablr.com");
        saveHotel("HTL-BLR-5", "The Chancery Pavilion", "BENGALURU", "Residency Road, Bengaluru", new BigDecimal("3900.00"), new BigDecimal("4.3"), 100, "stay@chanceryhotels.com");
        saveHotel("HTL-BLR-3", "Vivanta Bengaluru Residency Road", "BENGALURU", "MG Road Area, Bengaluru", new BigDecimal("6500.00"), new BigDecimal("4.5"), 120, "vivanta.blr@tajhotels.com");
        saveHotel("HTL-BLR-2", "ITC Gardenia", "BENGALURU", "Residency Road, Bengaluru", new BigDecimal("10500.00"), new BigDecimal("4.7"), 180, "itcgardenia@itchotels.in");
        saveHotel("HTL-BLR-1", "The Leela Palace Bengaluru", "BENGALURU", "23 Old Airport Road, Bengaluru", new BigDecimal("12500.00"), new BigDecimal("4.8"), 200, "reservations.blr@theleela.com");

        // MANGALURU
        saveHotel("HTL-IXE-3", "Avatar Hotel & Convention", "MANGALURU", "KSRTC Road, Bejai, Mangaluru", new BigDecimal("2900.00"), new BigDecimal("4.2"), 55, "frontdesk@avatarhotels.com");
        saveHotel("HTL-IXE-2", "Goldfinch Hotel Mangalore", "MANGALURU", "Bunts Hostel Road, Mangaluru", new BigDecimal("3800.00"), new BigDecimal("4.3"), 60, "reservations@goldfinchhotels.com");
        saveHotel("HTL-IXE-1", "The Ocean Pearl", "MANGALURU", "Navabharath Circle, Kodialbail, Mangaluru", new BigDecimal("4200.00"), new BigDecimal("4.5"), 80, "info@theoceanpearl.in");

        // MYSURU
        saveHotel("HTL-MYS-4", "Hotel Roopa Mysore", "MYSURU", "BN Road, Near Palace, Mysuru", new BigDecimal("1450.00"), new BigDecimal("3.8"), 45, "stay@hotelroopamysore.com");
        saveHotel("HTL-MYS-5", "Hotel Dasaprakash", "MYSURU", "Gandhi Square, Mysuru", new BigDecimal("1850.00"), new BigDecimal("4.0"), 50, "info@hoteldasaprakash.com");
        saveHotel("HTL-MYS-6", "Southern Star Mysore", "MYSURU", "Vinobha Road, Mysuru", new BigDecimal("3400.00"), new BigDecimal("4.3"), 70, "reservations@southernstarmysore.com");
        saveHotel("HTL-MYS-7", "Fortune JP Palace", "MYSURU", "Abba Road, Nazarbad, Mysuru", new BigDecimal("4200.00"), new BigDecimal("4.4"), 85, "fortune@jppalace.com");
        saveHotel("HTL-MYS-2", "Grand Mercure Mysuru", "MYSURU", "New Sayyaji Rao Road, Mysuru", new BigDecimal("5400.00"), new BigDecimal("4.5"), 90, "h8989@accor.com");
        saveHotel("HTL-MYS-1", "Radisson Blu Plaza Hotel Mysuru", "MYSURU", "MG Road, Mysuru", new BigDecimal("6200.00"), new BigDecimal("4.6"), 110, "reservations@radissonmysore.com");
        saveHotel("HTL-MYS-3", "Silent Shores Resort & Spa", "MYSURU", "BEML Layout, Hootagalli, Mysuru", new BigDecimal("7500.00"), new BigDecimal("4.6"), 70, "booking@silentshores.in");

        // UDUPI
        saveHotel("HTL-UD-1", "Fortune Inn Valley View", "UDUPI", "Madhav Nagar, Manipal, Udupi", new BigDecimal("4800.00"), new BigDecimal("4.4"), 75, "fivv@fortunehotels.in");
        saveHotel("HTL-UD-2", "Country Inn & Suites by Radisson Manipal", "UDUPI", "Rajiv Nagar, Manipal, Udupi", new BigDecimal("4500.00"), new BigDecimal("4.3"), 65, "manipal@countryinn.in");
        saveHotel("HTL-UD-3", "Hotel Mathura Comforts", "UDUPI", "Car Street, Near Krishna Temple, Udupi", new BigDecimal("2200.00"), new BigDecimal("4.1"), 40, "stay@mathuracomforts.com");

        // MUMBAI
        saveHotel("HTL-BOM-1", "The Taj Mahal Palace", "MUMBAI", "Apollo Bunder, Colaba, Mumbai", new BigDecimal("18500.00"), new BigDecimal("4.9"), 250, "tajpalace.mumbai@tajhotels.com");
        saveHotel("HTL-BOM-2", "Trident Nariman Point", "MUMBAI", "Marine Drive, Mumbai", new BigDecimal("13000.00"), new BigDecimal("4.7"), 220, "narimanpoint@tridenthotels.com");
        saveHotel("HTL-BOM-3", "JW Marriott Mumbai Juhu", "MUMBAI", "Juhu Tara Road, Mumbai", new BigDecimal("15000.00"), new BigDecimal("4.8"), 200, "jw.mumbai@marriott.com");

        // PUNE
        saveHotel("HTL-PUNE-1", "JW Marriott Hotel Pune", "PUNE", "Senapati Bapat Road, Pune", new BigDecimal("11000.00"), new BigDecimal("4.8"), 180, "pune.jw@marriott.com");
        saveHotel("HTL-PUNE-2", "Conrad Pune", "PUNE", "Mangaldas Road, Sangamvadi, Pune", new BigDecimal("12500.00"), new BigDecimal("4.8"), 160, "conradpune@hilton.com");
        saveHotel("HTL-PUNE-3", "Hyatt Regency Pune", "PUNE", "Weikfield IT Park, Viman Nagar, Pune", new BigDecimal("8500.00"), new BigDecimal("4.6"), 150, "regency.pune@hyatt.com");

        // CHENNAI
        saveHotel("HTL-MAA-1", "The Leela Palace Chennai", "CHENNAI", "Adyar Seaface, MRC Nagar, Chennai", new BigDecimal("13500.00"), new BigDecimal("4.8"), 210, "reservations.maa@theleela.com");
        saveHotel("HTL-MAA-2", "ITC Grand Chola", "CHENNAI", "Mount Road, Guindy, Chennai", new BigDecimal("12000.00"), new BigDecimal("4.8"), 240, "grandchola@itchotels.in");
        saveHotel("HTL-MAA-3", "Taj Coromandel", "CHENNAI", "Nungambakkam High Road, Chennai", new BigDecimal("10500.00"), new BigDecimal("4.7"), 170, "coromandel.chennai@tajhotels.com");

        // HYDERABAD
        saveHotel("HTL-HYD-1", "Taj Falaknuma Palace", "HYDERABAD", "Engine Bowli, Fatima Nagar, Hyderabad", new BigDecimal("24000.00"), new BigDecimal("4.9"), 60, "falaknuma.hyderabad@tajhotels.com");
        saveHotel("HTL-HYD-2", "ITC Kohenur", "HYDERABAD", "HITEC City, Madhapur, Hyderabad", new BigDecimal("12500.00"), new BigDecimal("4.8"), 190, "itckohenur@itchotels.in");
        saveHotel("HTL-HYD-3", "The Park Hyderabad", "HYDERABAD", "Somajiguda, Raj Bhavan Road, Hyderabad", new BigDecimal("6500.00"), new BigDecimal("4.4"), 130, "resv.hyd@theparkhotels.com");
    }

    private void saveHotel(String id, String name, String city, String address, BigDecimal price, BigDecimal rating, int rooms, String email) {
        if (!hotelRepo.existsById(id)) {
            hotelRepo.save(HotelEntity.builder()
                    .id(id)
                    .name(name)
                    .city(city)
                    .address(address)
                    .pricePerNight(price)
                    .rating(rating)
                    .roomsTotal(rooms)
                    .contactEmail(email)
                    .build());
        }
    }

    private void seedCabs() {
        String[] cities = {"HUBBALLI", "GOA", "BENGALURU", "MANGALURU", "MYSURU", "UDUPI", "MUMBAI", "PUNE", "CHENNAI", "HYDERABAD"};
        for (String city : cities) {
            String olaId = "CAB-OLA-" + city;
            if (!cabRepo.existsById(olaId)) {
                cabRepo.save(CabServiceEntity.builder()
                        .id(olaId)
                        .provider("Ola Cabs")
                        .city(city)
                        .baseFare(new BigDecimal("100.00"))
                        .perKm(new BigDecimal("18.00"))
                        .build());
            }

            String uberId = "CAB-UBER-" + city;
            if (!cabRepo.existsById(uberId)) {
                cabRepo.save(CabServiceEntity.builder()
                        .id(uberId)
                        .provider("Uber India")
                        .city(city)
                        .baseFare(new BigDecimal("90.00"))
                        .perKm(new BigDecimal("19.50"))
                        .build());
            }
        }
    }
}

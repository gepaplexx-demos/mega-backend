package com.gepardec.mega.service.mapper;

import com.gepardec.mega.domain.model.Employee;
import com.gepardec.mega.domain.utils.DateUtils;
import de.provantis.zep.BeschaeftigungszeitType;
import de.provantis.zep.MitarbeiterType;
import de.provantis.zep.RegelarbeitszeitListeTypeTs;
import de.provantis.zep.RegelarbeitszeitType;

import javax.enterprise.context.ApplicationScoped;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class EmployeeMapper {

    public Employee map(MitarbeiterType mitarbeiterType) {
        if (mitarbeiterType == null) {
            return null;
        }
        return Employee.builder()
                .userId(mitarbeiterType.getUserId())
                .email(mitarbeiterType.getEmail())
                .title(mitarbeiterType.getTitel())
                .firstname(mitarbeiterType.getVorname())
                .lastname(mitarbeiterType.getNachname())
                .salutation(mitarbeiterType.getAnrede())
                .releaseDate(getCorrectReleaseDate(mitarbeiterType))
                .workDescription(mitarbeiterType.getPreisgruppe())
                .language(mitarbeiterType.getSprache())
                .regularWorkingHours(getRegularWorkinghours(mitarbeiterType.getRegelarbeitszeitListe()))
                .active(hasEmployeeAndActiveEmployment(mitarbeiterType))
                .build();
    }

    private String getCorrectReleaseDate(MitarbeiterType mitarbeiterType) {
        boolean isReleaseDateNull = mitarbeiterType.getFreigabedatum() == null || mitarbeiterType.getFreigabedatum().equalsIgnoreCase("null");
        boolean isCreatedDateNull = mitarbeiterType.getCreated() == null;
        if (isReleaseDateNull) {
            if (isCreatedDateNull) {
                return null;
            }
            return LocalDateTime.parse(mitarbeiterType.getCreated(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).format(DateTimeFormatter.ISO_LOCAL_DATE);
        }

        return mitarbeiterType.getFreigabedatum();
    }

    private Map<DayOfWeek, Double> getRegularWorkinghours(RegelarbeitszeitListeTypeTs regelarbeitszeitListeTypeTs){
        if(regelarbeitszeitListeTypeTs == null) return new HashMap<>();

        List <RegelarbeitszeitType> regelarbeitszeitList = regelarbeitszeitListeTypeTs.getRegelarbeitszeit();
        RegelarbeitszeitType regelarbeitszeitType = regelarbeitszeitList.get(regelarbeitszeitList.size()-1);

        return new HashMap<>(Map.ofEntries(
                Map.entry(DayOfWeek.MONDAY, regelarbeitszeitType.getMontag()),
                Map.entry(DayOfWeek.TUESDAY, regelarbeitszeitType.getDienstag()),
                Map.entry(DayOfWeek.WEDNESDAY, regelarbeitszeitType.getMittwoch()),
                Map.entry(DayOfWeek.THURSDAY, regelarbeitszeitType.getDonnerstag()),
                Map.entry(DayOfWeek.FRIDAY, regelarbeitszeitType.getFreitag())
        ));
    }

    private boolean hasEmployeeAndActiveEmployment(final MitarbeiterType employee) {
        boolean active = false;
        if (employee.getBeschaeftigungszeitListe() != null) {
            final LocalDate now = LocalDate.now();
            final List<BeschaeftigungszeitType> employments = employee.getBeschaeftigungszeitListe().getBeschaeftigungszeit();
            active = hasOpenEmployments(employments, now) || hasEmploymentEndInTheFuture(employments, now);
        }

        return active;
    }

    private boolean hasOpenEmployments(final List<BeschaeftigungszeitType> employments, final LocalDate now) {
        return employments.stream()
                .filter(employment -> isDateLessOrEqual(employment.getStartdatum(), now))
                .anyMatch(employment -> employment.getEnddatum() == null);
    }

    private boolean hasEmploymentEndInTheFuture(final List<BeschaeftigungszeitType> employments, final LocalDate now) {
        return employments.stream()
                .filter(employment -> isDateLessOrEqual(employment.getStartdatum(), now))
                .anyMatch(employment -> isDateGreaterOrEqual(employment.getEnddatum(), now));
    }

    private boolean isDateLessOrEqual(final String date, final LocalDate now) {
        return DateUtils.parseDate(date).compareTo(now) <= 0;
    }

    private boolean isDateGreaterOrEqual(final String date, final LocalDate now) {
        return DateUtils.parseDate(date).compareTo(now) >= 0;
    }
}

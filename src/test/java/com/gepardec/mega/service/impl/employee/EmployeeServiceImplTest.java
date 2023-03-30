package com.gepardec.mega.service.impl.employee;

import com.gepardec.mega.domain.model.Employee;
import com.gepardec.mega.service.api.EmployeeService;
import com.gepardec.mega.service.impl.EmployeeServiceImpl;
import com.gepardec.mega.zep.ZepService;
import com.gepardec.mega.zep.ZepServiceException;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.assertj.core.api.SoftAssertions;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@QuarkusTest
class EmployeeServiceImplTest {

    @InjectMock
    ZepService zepService;

    @InjectMock
    ManagedExecutor managedExecutor;

    @Inject
    Logger logger;

    @Inject
    EmployeeService employeeService;

    @BeforeEach
    void setUp() {
        employeeService = new EmployeeServiceImpl(logger, zepService, managedExecutor, 10);
    }

    @Test
    void testGetEmployee() {
        Mockito.when(zepService.getEmployee(Mockito.any())).thenReturn(createEmployee(0));

        final Employee employee = employeeService.getEmployee("someuserid");
        assertThat(employee).isNotNull();
        assertThat(employee.getUserId()).isEqualTo("0");
        assertThat(employee.getFirstname()).isEqualTo("Max_0");
    }

    @Test
    void testGetEmployees() {
        final Employee employee0 = createEmployee(0);
        final Employee employee1 = createEmployeeWithActive(1, false);

        Mockito.when(zepService.getEmployees()).thenReturn(List.of(employee0, employee1));

        final List<Employee> employees = employeeService.getAllActiveEmployees();

        assertAll(
                () -> assertThat(employees).isNotNull(),
                () -> assertThat(employees).hasSize(1),
                () -> assertThat(employees.get(0).getUserId()).isEqualTo("0"),
                () -> assertThat(employees.get(0).getFirstname()).isEqualTo("Max_0")
        );
    }

    @Test
    void testGetEmployeesConsideringExitDate() {

        int selectedYear = 2023;
        int selectedMonth = 3;

        final String EMPLOYEE_0 = "employee0";
        final String EMPLOYEE_EXIT_SELECTED_MONTH = "employeeExitSelectedMonth";
        final String EMPLOYEE_EXIT_NEXT_MONTH = "employeeExitNextMonth";
        final String EMPLOYEE_EXIT_LAST_MONTH = "employeeExitLastMonth";

        // Default case
        final Employee employee0 = createEmployee(0);
        employee0.setFirstname(EMPLOYEE_0);

        // Employee hat 03/2023 gekündigt & 03/2023 ist in der gui selektiert, daher soll der PL/Office ihn sehen
        final Employee employeeExitSelectedMonth = createEmployeeWithActive(1, false);
        employeeExitSelectedMonth.setFirstname(EMPLOYEE_EXIT_SELECTED_MONTH);
        employeeExitSelectedMonth.setExitDate(LocalDate.of(selectedYear, selectedMonth, 1));

        // Employee hat 04/2023 gekündigt & 03/2023 ist in der gui selektiert, d.h. im selektierten zeitraum (1 monat vorher)
        // war er ja noch normal angestellt, daher soll der PL/Office ihn sehen
        final Employee employeeExitNextMonth = createEmployeeWithActive(1, false);
        employeeExitNextMonth.setFirstname(EMPLOYEE_EXIT_NEXT_MONTH);
        employeeExitNextMonth.setExitDate(LocalDate.of(selectedYear, selectedMonth + 1, 1));

        // Employee hat 02/2023 gekündigt & 03/2023 ist in der gui selektiert, d.h. im selektierten zeitraum (1 monat nachher)
        // war er nicht mehr angestellt und es gibt keine StepEntries, daher soll der PL/Office ihn NICHT sehen
        final Employee employeeExitLastMonth = createEmployeeWithActive(1, false);
        employeeExitLastMonth.setFirstname(EMPLOYEE_EXIT_LAST_MONTH);
        employeeExitLastMonth.setExitDate(LocalDate.of(selectedYear, selectedMonth - 1, 1));


        Mockito.when(zepService.getEmployees()).thenReturn(List.of(
                employee0,
                employeeExitSelectedMonth,
                employeeExitNextMonth,
                employeeExitLastMonth
        ));

        YearMonth selectedYearMonth = YearMonth.of(selectedYear, selectedMonth);
        final List<Employee> employees = employeeService.getAllEmployeesConsideringExitDate(selectedYearMonth);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(employees).isNotNull();
            softly.assertThat(getFirstnameList(employees)).containsAll(
                    List.of(EMPLOYEE_0, EMPLOYEE_EXIT_SELECTED_MONTH, EMPLOYEE_EXIT_NEXT_MONTH)
            );
            softly.assertThat(getFirstnameList(employees)).doesNotContain(EMPLOYEE_EXIT_LAST_MONTH);
        });
    }

    private List<String> getFirstnameList(List<Employee> employees) {
        return employees.stream().map(Employee::getFirstname).collect(Collectors.toList());
    }

    @Test
    void testUpdateEmployeesReleaseDate_EmployeesNull() {
        assertThatThrownBy(() -> employeeService.updateEmployeesReleaseDate(null)).isInstanceOf(ZepServiceException.class);
    }

    @Test
    void testUpdateEmployeesReleaseDate_EmployeesEmpty() {
        assertThat(employeeService.updateEmployeesReleaseDate(new ArrayList<>())).isEmpty();
    }

    @Test
    void testUpdateEmployeesReleaseDate_EmployeesNotEmpty_EmployeeError() {
        Mockito.doThrow(new ZepServiceException()).when(zepService).updateEmployeesReleaseDate(Mockito.any(), Mockito.any());
        Mockito.doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(managedExecutor).execute(Mockito.any());

        final List<String> result = employeeService.updateEmployeesReleaseDate(List.of(createEmployee(0)));

        assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0)).isEqualTo("0")
        );
    }

    @Test
    void testUpdateEmployeesReleaseDate_EmployeesNotEmpty_ThreadingError() {
        AtomicInteger count = new AtomicInteger();

        Mockito.doAnswer(invocation -> {
            count.getAndIncrement();
            ((Runnable) invocation.getArgument(0)).run();
            if (count.get() == 1) {
                throw new ExecutionException(new IllegalStateException());
            } else {
                return null;
            }
        }).when(managedExecutor).execute(Mockito.any());

        final List<Employee> employees = IntStream.range(0, 40).mapToObj(this::createEmployee).collect(Collectors.toList());

        final List<String> result = employeeService.updateEmployeesReleaseDate(employees);

        assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result).hasSize(10),
                () -> assertThat(result.get(0)).isEqualTo("0"),
                () -> assertThat(result.get(9)).isEqualTo("9")
        );
    }

    @Test
    void testUpdateEmployeesReleaseDate_EmployeesNotEmpty_EmployeOk() {
        Mockito.doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(managedExecutor).execute(Mockito.any());

        final List<String> result = employeeService.updateEmployeesReleaseDate(List.of(createEmployee(0)));

        assertThat(result).isNotNull();
    }

    private Employee createEmployee(final int userId) {
        return createEmployeeWithActive(userId, true);
    }

    private Employee createEmployeeWithActive(final int userId, boolean active) {
        final String name = "Max_" + userId;

        return Employee.builder()
                .email(name + "@gepardec.com")
                .firstname(name)
                .lastname(name + "_Nachname")
                .title("Ing.")
                .userId(String.valueOf(userId))
                .salutation("Herr")
                .workDescription("ARCHITEKT")
                .releaseDate("2020-01-01")
                .active(active)
                .build();
    }
}

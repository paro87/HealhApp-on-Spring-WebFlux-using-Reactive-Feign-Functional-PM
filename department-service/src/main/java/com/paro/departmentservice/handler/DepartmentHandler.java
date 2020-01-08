package com.paro.departmentservice.handler;

import com.paro.departmentservice.client.PatientClient;
import com.paro.departmentservice.model.Department;
import com.paro.departmentservice.model.Patient;
import com.paro.departmentservice.repository.DepartmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.BodyInserters.fromPublisher;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Component

public class DepartmentHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DepartmentHandler.class);

    private final PatientClient patientClient;
    private final DepartmentRepository departmentRepository;

    @Autowired
    public DepartmentHandler (DepartmentRepository departmentRepository, PatientClient patientClient) {
        this.departmentRepository=departmentRepository;
        this.patientClient=patientClient;
    }



    public Mono<ServerResponse> getByDepartmentId(ServerRequest request) {
        Long patientId= Long.valueOf(request.pathVariable("id"));
        return ok().contentType(MediaType.APPLICATION_JSON).body(departmentRepository.findByDepartmentId(patientId), Department.class);

    }
    
    /*public Mono<ServerResponse> getAll(ServerRequest request) {
        return ok().contentType(MediaType.APPLICATION_JSON).body(departmentRepository.findAll(), Department.class);
    }*/

    //Knoldus
    public Mono<ServerResponse> getAll(ServerRequest request) {
        return ok().contentType(MediaType.APPLICATION_JSON).body(fromPublisher(departmentRepository.findAll(), Department.class));
    }

    public Mono<ServerResponse> add(ServerRequest request) {
        //1-The only solution for saving Mono - https://stackoverflow.com/questions/47918441/why-spring-reactivemongorepository-doest-have-save-method-for-mono
        Mono<Department> department=request.bodyToMono(Department.class);
        Mono<Department> departmentSaved=department.flatMap(entity -> departmentRepository.save(entity));
        return ServerResponse.status(HttpStatus.CREATED).body(departmentSaved, Department.class);

        //2
        /*Mono<Department> departmentAdded= departmentRepository.saveAll(department).next();
        return departmentAdded;*/

        //3
        // return ServerResponse.ok().build(departmentRepository.saveAll(request.bodyToMono(Department.class)).next());
    }

    public Mono<ServerResponse> put(ServerRequest request) {
        Long departmentId= Long.valueOf(request.pathVariable("id"));
        Mono<Department> departmentToPut=request.bodyToMono(Department.class);
        Mono<Department> departmentPut=departmentRepository.findByDepartmentId(departmentId)
                .flatMap(department ->departmentRepository.delete(department))
                .then(departmentRepository.saveAll(departmentToPut).next());

        return ServerResponse.status(HttpStatus.OK).body(departmentPut, Department.class);

        // Possible 2nd solution
        /*
        Long departmentId= Long.valueOf(request.pathVariable("id"));
        Mono<Department> departmentToPut=request.bodyToMono(Department.class);
        Mono<Department> departmentFound=departmentRepository.findByDepartmentId(departmentId);
        Mono<Department> departmentPut=departmentFound.map(departmentFromRepo -> {
            departmentToPut.map(departmentBeingPut -> {
                if (departmentBeingPut.getName()!=null) {
                    departmentFromRepo.setName(departmentBeingPut.getName());
                }
                if (departmentBeingPut.getHospitalId()!=null) {
                    departmentFromRepo.setHospitalId(departmentBeingPut.getHospitalId());
                }
                if (departmentBeingPut.getPatientList()!=null) {
                    departmentFromRepo.setPatientList(departmentBeingPut.getPatientList());
                }
                return departmentFromRepo;
            }).subscribe();
            return departmentFromRepo;
        });
        return ServerResponse.status(HttpStatus.OK).body(departmentPut, Department.class);*/
    }

    public Mono<ServerResponse> patch(ServerRequest request) {
        Long departmentId= Long.valueOf(request.pathVariable("id"));
        Mono<Department> departmentToPatch=request.bodyToMono(Department.class);

        Mono<Department> departmentFound=departmentRepository.findByDepartmentId(departmentId);
        Mono<Department> departmentPatched=departmentFound.map(departmentFromRepo -> {

            departmentToPatch.map(departmentBeingPatched -> {

                if (departmentBeingPatched.getDepartmentId()!=null) {
                    departmentFromRepo.setDepartmentId(departmentBeingPatched.getDepartmentId());
                }
                if (departmentBeingPatched.getName()!=null) {
                    departmentFromRepo.setName(departmentBeingPatched.getName());
                }
                if (departmentBeingPatched.getHospitalId()!=null) {
                    departmentFromRepo.setHospitalId(departmentBeingPatched.getHospitalId());
                }
                if (departmentBeingPatched.getPatientList()!=null) {
                    departmentFromRepo.setPatientList(departmentBeingPatched.getPatientList());
                }
                return departmentFromRepo;
            }).subscribe();
            return departmentFromRepo;
        });
        //return departmentRepository.saveAll(departmentPatched).next();
        return ServerResponse.status(HttpStatus.CREATED).body(departmentRepository.saveAll(departmentPatched).next(), Department.class);
    }

    public Mono<ServerResponse> delete(ServerRequest request) {
        Long departmentId= Long.valueOf(request.pathVariable("id"));
        Mono<Department> departmentToBeDeleted=departmentRepository.findByDepartmentId(departmentId);
        return departmentToBeDeleted.flatMap(department -> ServerResponse.noContent().build(departmentRepository.delete(department)))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> getByHospitalId(ServerRequest request) {
        Long hospitalId= Long.valueOf(request.pathVariable("hospitalId"));
        return ok().body(departmentRepository.findByHospitalId(hospitalId), Department.class);
    }


    public Mono<ServerResponse> getByHospitalWithPatients(ServerRequest request) {
        Long hospitalId= Long.valueOf(request.pathVariable("hospitalId"));
        Flux<Department> departmentList = departmentRepository.findByHospitalId(hospitalId);
        Flux<Department> departmentFlux= departmentList.flatMap(department -> {
            Flux<Patient> patientFlux = patientClient.findByDepartment(department.getDepartmentId());
            return patientFlux.collectList().map(list -> {
                department.setPatientList(list);
                return department;
            });
        });

        return ok().contentType(MediaType.APPLICATION_JSON).body(fromPublisher(departmentFlux, Department.class));

    }
}

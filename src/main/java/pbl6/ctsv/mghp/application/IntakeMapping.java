package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.*;
import static pbl6.ctsv.mghp.application.ApplicationEntities.*;

final class IntakeMapping {
    private IntakeMapping() {}

    static Map<String,Object> map(Object... fields) {
        var result=new LinkedHashMap<String,Object>();
        for(int i=0;i<fields.length;i+=2) result.put((String)fields[i],wire(fields[i+1]));
        return result;
    }

    static Object wire(Object value) {
        if(value instanceof Long id) return id.toString();
        if(value instanceof OffsetDateTime time) return time.toInstant().toString();
        if(value instanceof java.time.LocalDate day) return day.toString();
        if(value instanceof UUID id) return id.toString();
        return value;
    }

    static Map<String,Object> wire(Map<String,Object> values) {
        var result=new LinkedHashMap<String,Object>();
        values.forEach((key,value) -> result.put(key,wire(value)));
        return result;
    }

    static Map<String,Object> summary(Application root) {
        return map("id",root.id,"code",root.code,"roundId",root.roundId,"studyProcessId",root.processId,
            "policyId",root.policyId,"policyVersionId",root.policyVersionId,"status",root.status,"submittedAt",root.submittedAt);
    }

    static Map<String,Object> version(Submission v) {
        return map("id",v.id,"revision",v.revision,"submittedAt",v.submittedAt,"submissionType",v.type);
    }

    static Map<String,Object> acknowledgement(Application root,Submission v) {
        return map("applicationId",root.id,"versionId",v.id,"revision",v.revision,"operationId",v.operationId,
            "submittedAt",v.submittedAt,"submissionType",v.type,"comparisonStatus",v.comparisonStatus);
    }

    static Map<String,Object> document(Document d,Long file,Integer personIndex) {
        return map("id",d.id,"requirementId",d.requirementId,"fileId",file,"documentNumber",d.documentNumber,
            "issuer",d.issuer,"issuedOn",d.issuedOn,"expiresOn",d.expiresOn,"relatedPersonIndex",personIndex);
    }

    static Map<String,Object> claims(Submission row) {
        return map("fullName",row.fullName,"citizenId",row.citizenId,"birthDate",row.birthDate,
            "birthPlace",row.birthPlace,"gender",row.gender,"ethnicityId",row.ethnicityId,
            "classId",row.classId,"studentCode",row.studentCode,"cohortId",row.cohortId,
            "managementUnitId",row.managementUnitId,"permanentAddress",row.permanentAddress,"phoneNumber",row.phoneNumber);
    }

    static void claims(Submission row,JsonNode values) {
        IntakeRules.shape(values,IntakeRules.CLAIMS);
        row.fullName=IntakeRules.text(values.get("fullName"),"fullName",512,true);
        row.citizenId=IntakeRules.text(values.get("citizenId"),"citizenId",64,true);
        row.birthDate=IntakeRules.date(values.get("birthDate"),"birthDate",true);
        row.birthPlace=IntakeRules.text(values.get("birthPlace"),"birthPlace",512,true);
        row.gender=IntakeRules.text(values.get("gender"),"gender",128,true);
        row.ethnicityId=IntakeRules.id(values.get("ethnicityId"),"ethnicityId");
        row.classId=IntakeRules.id(values.get("classId"),"classId");
        row.studentCode=IntakeRules.text(values.get("studentCode"),"studentCode",64,true);
        row.cohortId=IntakeRules.id(values.get("cohortId"),"cohortId");
        row.managementUnitId=IntakeRules.id(values.get("managementUnitId"),"managementUnitId");
        row.permanentAddress=IntakeRules.text(values.get("permanentAddress"),"permanentAddress",1048576,true);
        row.phoneNumber=IntakeRules.text(values.get("phoneNumber"),"phoneNumber",64,true);
    }

    static Map<String,Object> people(RelatedPerson row) {
        return map("relation",row.relation,"fullName",row.fullName,"citizenId",row.citizenId,
            "ethnicityId",row.ethnicityId,"permanentAddress",row.permanentAddress,"localityCode",row.localityCode,
            "workplaceUnitId",row.workplaceUnitId,"workplaceName",row.workplaceName,"workplaceGroup",row.workplaceGroup,
            "employmentStatus",row.employmentStatus,"statusFrom",row.statusFrom,"statusUntil",row.statusUntil,
            "matchingStaffCode",row.matchingStaffCode);
    }

    static void people(RelatedPerson row,JsonNode values) {
        IntakeRules.shape(values,IntakeRules.PEOPLE);
        row.relation=IntakeRules.text(values.get("relation"),"relation",128,false);
        row.fullName=IntakeRules.text(values.get("fullName"),"fullName",512,false);
        row.citizenId=IntakeRules.text(values.get("citizenId"),"citizenId",64,false);
        row.ethnicityId=IntakeRules.nullableId(values,"ethnicityId");
        row.permanentAddress=IntakeRules.text(values.get("permanentAddress"),"permanentAddress",1048576,false);
        row.localityCode=IntakeRules.text(values.get("localityCode"),"localityCode",128,false);
        row.workplaceUnitId=IntakeRules.nullableId(values,"workplaceUnitId");
        row.workplaceName=IntakeRules.text(values.get("workplaceName"),"workplaceName",512,false);
        row.workplaceGroup=IntakeRules.text(values.get("workplaceGroup"),"workplaceGroup",128,false);
        row.employmentStatus=IntakeRules.text(values.get("employmentStatus"),"employmentStatus",128,false);
        row.statusFrom=IntakeRules.date(values.get("statusFrom"),"statusFrom",false);
        row.statusUntil=IntakeRules.date(values.get("statusUntil"),"statusUntil",false);
        row.matchingStaffCode=IntakeRules.text(values.get("matchingStaffCode"),"matchingStaffCode",128,false);
    }

    static Map<String,Object> statements(Submission row) {
        return map("firstDegree",row.firstDegree,"alreadyBenefitedAtSameLevel",row.alreadyBenefitedAtSameLevel,"salariedWhileStudying",row.salariedWhileStudying,
            "declarationConfirmed",row.declarationConfirmed);
    }

    static void statements(Submission row,JsonNode values) {
        IntakeRules.shape(values,IntakeRules.STATEMENTS);
        row.firstDegree=IntakeRules.bool(values.get("firstDegree"),"firstDegree");
        row.alreadyBenefitedAtSameLevel=IntakeRules.bool(values.get("alreadyBenefitedAtSameLevel"),"alreadyBenefitedAtSameLevel");
        row.salariedWhileStudying=IntakeRules.bool(values.get("salariedWhileStudying"),"salariedWhileStudying");
        row.declarationConfirmed=IntakeRules.bool(values.get("declarationConfirmed"),"declarationConfirmed");
    }
}

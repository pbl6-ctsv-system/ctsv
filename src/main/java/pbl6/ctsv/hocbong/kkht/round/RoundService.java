package pbl6.ctsv.hocbong.kkht.round;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import pbl6.ctsv.foundation.api.AcademicSemesterSource;
import pbl6.ctsv.hocbong.kkht.round.entity.*;

/** Caller owns the authenticated transaction. T01 deliberately does not load grades or finance. */
class RoundService {
    private static final int CANDIDATE_PAGE_SIZE=100;
    private static final ZoneId VIETNAM=ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Comparator<RoundDtos.SourceSemesterDto> DEFAULT_ORDER=
        Comparator.comparing(RoundDtos.SourceSemesterDto::startDate).reversed()
            .thenComparing(RoundDtos.SourceSemesterDto::academicYear,Comparator.reverseOrder())
            .thenComparing(RoundDtos.SourceSemesterDto::semesterOrder,Comparator.reverseOrder())
            .thenComparing(RoundDtos.SourceSemesterDto::code)
            .thenComparingLong(value->Long.parseLong(value.sourceSemesterId()));
    private final RoundRepository repo;
    private final AcademicSemesterSource source;
    private final ObjectMapper json;
    private final Clock clock;

    RoundService(RoundRepository repo,AcademicSemesterSource source,ObjectMapper json,Clock clock) {
        this.repo=repo; this.source=source; this.json=json; this.clock=clock;
    }
    RoundDtos.DefaultSemester defaultSemester() {
        var today=LocalDate.ofInstant(clock.instant(),VIETNAM);
        RoundDtos.SourceSemesterDto best=null;
        for(int page=0;;page++) {
            var candidates=source.findStartedPage(today,page,CANDIDATE_PAGE_SIZE);
            if(candidates.items().isEmpty()) break;
            for(var candidate:sourceDtos(candidates.items())) {
                // Candidates are date-descending. Finish the whole best-date group, including page boundaries.
                if(best!=null && candidate.startDate()!=null && candidate.startDate().compareTo(best.startDate())<0)
                    return new RoundDtos.DefaultSemester(best,null);
                if(candidate.selectable() && !LocalDate.parse(candidate.startDate()).isAfter(today)
                        && (best==null || DEFAULT_ORDER.compare(candidate,best)<0)) best=candidate;
            }
            if(((long)page+1)*CANDIDATE_PAGE_SIZE>=candidates.totalElements()) break;
        }
        return new RoundDtos.DefaultSemester(best,best==null?"NO_ELIGIBLE_SEMESTER":null);
    }
    RoundDtos.SourceSemesterDto sourceById(long id) {
        var value=source.findById(id).orElseThrow(()->new RoundFailure(404,"NOT_FOUND"));
        return sourceDto(value,assess(value));
    }
    RoundDtos.Page<RoundDtos.SourceSemesterDto> sources(String q,int[] page) {
        var result=source.findPage(q,page[0],page[1]);
        return new RoundDtos.Page<>(sourceDtos(result.items()),result.page(),result.size(),result.totalElements());
    }
    private List<RoundDtos.SourceSemesterDto> sourceDtos(List<AcademicSemesterSource.Semester> values) {
        var mapped=repo.semestersBySources(values.stream().map(AcademicSemesterSource.Semester::id).toList());
        var periods=values.stream().flatMap(value->RoundRules.period(value.code()).stream()).toList();
        var canonical=repo.semestersByPeriods(periods);
        return values.stream().map(value -> {
            var period=RoundRules.period(value.code()).orElse(null);
            var semester=mapped.get(value.id());
            if(semester==null&&period!=null) semester=canonical.get(RoundRepository.periodKey(period.academicYear(),period.semesterOrder()));
            return sourceDto(value,assess(value,period,semester));
        }).toList();
    }
    private record Assessment(RoundRules.Period period,ScholarshipSemester semester,String reason) {}

    private Assessment assess(AcademicSemesterSource.Semester value) {
        var period=RoundRules.period(value.code()).orElse(null);
        var alias=repo.mappingBySource(value.id());
        ScholarshipSemester semester=alias.map(m->repo.semester(m.getSemesterId())).orElseGet(() ->
            period==null?null:repo.semesterByPeriod(period.academicYear(),period.semesterOrder()).orElse(null));
        return assess(value,period,semester);
    }
    private Assessment assess(AcademicSemesterSource.Semester value,RoundRules.Period period,ScholarshipSemester semester) {
        String reason=null;
        if(!value.present()) reason="SOURCE_NOT_PRESENT";
        else if(period!=null&&period.summer()) reason="SUMMER_MERGED_INTO_SEMESTER_2";
        else if(value.startDate()==null || value.endDate()!=null && value.startDate().isAfter(value.endDate()))
            reason="SOURCE_DATES_UNVERIFIED";
        else if(period==null) reason="SEMESTER_MAPPING_REQUIRED";
        else if(semester!=null && (!period.academicYear().equals(semester.getAcademicYear())
                || period.semesterOrder()!=semester.getSemesterOrder()
                || !value.startDate().equals(semester.getStartDate())))
            reason="SEMESTER_MAPPING_CONFLICT";
        return new Assessment(period,semester,reason);
    }
    private RoundDtos.SourceSemesterDto sourceDto(AcademicSemesterSource.Semester value,Assessment a) {
        return new RoundDtos.SourceSemesterDto(Long.toString(value.id()),value.code(),value.name(),
            a.period()==null?null:a.period().academicYear(),a.period()==null?null:a.period().semesterOrder(),
            value.startDate()==null?null:value.startDate().toString(),value.endDate()==null?null:value.endDate().toString(),
            a.semester()==null?null:a.semester().getId().toString(),a.reason()==null,a.reason());
    }

    RoundDtos.Page<RoundDtos.RoundSummary> list(Long semesterId,Long sourceSemesterId,Integer number,int[] page) {
        if(semesterId!=null&&sourceSemesterId!=null) throw RoundFailure.invalid("semesterId");
        if(sourceSemesterId!=null) {
            var mapping=repo.mappingBySource(sourceSemesterId);
            if(mapping.isPresent()) semesterId=mapping.get().getSemesterId();
            else {
                var period=source.findById(sourceSemesterId).flatMap(s->RoundRules.period(s.code()));
                if(period.isEmpty()) return new RoundDtos.Page<>(List.of(),page[0],page[1],0);
                var s=repo.semesterByPeriod(period.get().academicYear(),period.get().semesterOrder());
                if(s.isEmpty()) return new RoundDtos.Page<>(List.of(),page[0],page[1],0);
                semesterId=s.get().getId();
            }
        }
        return repo.list(semesterId,number,page);
    }
    RoundDtos.View detail(long id) {
        byte[] version=repo.lockRound(id);
        var row=repo.round(id);
        var body=RoundMapper.detail(row,repo.semester(row.getSemesterId()),repo.mapping(row.getSourceMappingId()));
        return new RoundDtos.View(body,RoundRules.tag(id,version,body,json));
    }
}

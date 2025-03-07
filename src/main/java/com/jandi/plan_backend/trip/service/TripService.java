package com.jandi.plan_backend.trip.service;

import com.jandi.plan_backend.image.service.ImageService;
import com.jandi.plan_backend.trip.dto.MyTripRespDTO;
import com.jandi.plan_backend.trip.dto.TripItemRespDTO;
import com.jandi.plan_backend.trip.dto.TripLikeRespDTO;
import com.jandi.plan_backend.trip.dto.TripRespDTO;
import com.jandi.plan_backend.trip.entity.Trip;
import com.jandi.plan_backend.trip.entity.TripLike;
import com.jandi.plan_backend.trip.repository.TripLikeRepository;
import com.jandi.plan_backend.trip.repository.TripRepository;
import com.jandi.plan_backend.user.entity.City;
import com.jandi.plan_backend.user.entity.User;
import com.jandi.plan_backend.user.repository.CityRepository;
import com.jandi.plan_backend.util.ValidationUtil;
import com.jandi.plan_backend.util.service.BadRequestExceptionMessage;
import com.jandi.plan_backend.util.service.PaginationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TripService {
    private final TripRepository tripRepository;
    private final TripLikeRepository tripLikeRepository;
    private final ValidationUtil validationUtil;
    private final ImageService imageService;
    private final CityRepository cityRepository;
    private final String urlPrefix = "https://storage.googleapis.com/plan-storage/";
    private final Sort sortByCreate = Sort.by(Sort.Direction.DESC, "createdAt"); //생성일 역순

    public TripService(TripRepository tripRepository,
                       TripLikeRepository tripLikeRepository,
                       ValidationUtil validationUtil,
                       ImageService imageService,
                       CityRepository cityRepository
    ) {
        this.tripRepository = tripRepository;
        this.tripLikeRepository = tripLikeRepository;
        this.validationUtil = validationUtil;
        this.imageService = imageService;
        this.cityRepository = cityRepository;
    }

    /** 내 여행 계획 목록 전체 조회 (본인 명의의 계획만 조회) */
    public Page<MyTripRespDTO> getAllMyTrips(String userEmail, int page, int size) {
        User user = validationUtil.validateUserExists(userEmail);
        long totalCount = tripRepository.countByUser(user);

        return PaginationService.getPagedData(page, size, totalCount,
                pageable -> tripRepository.findByUser(user, // 본인 계획만 선택
                        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortByCreate)), //최근 생성된 순
                trip -> {
                    TripRespDTO tripRespDTO = convertToTripRespDTO(trip);
                    return new MyTripRespDTO(tripRespDTO, trip.getPrivatePlan());
                });
    }

    /** 좋아요한 여행 계획 목록 조회 */
    public Page<TripRespDTO> getLikedTrips(String userEmail, int page, int size) {
        User user = validationUtil.validateUserExists(userEmail);
        long totalCount = tripLikeRepository.countByUser(user);

        return PaginationService.getPagedData(page, size, totalCount,
                pageable -> tripLikeRepository.findByUser(user,
                        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortByCreate)), //최근 생성된 순
                tripLikeObj -> {
                    TripLike tripLike = (TripLike) tripLikeObj;
                    return convertToTripRespDTO(tripLike.getTrip());
                });
    }

    /** 전체 여행 계획 조회 */
    public Page<TripRespDTO> getAllTrips(String userEmail, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "tripId");

        if(userEmail == null) {
            // 미로그인: 공개 플랜만 조회
            long totalCount = tripRepository.countByPrivatePlan(false);
            return PaginationService.getPagedData(page, size, totalCount,
                    pageable -> tripRepository.findByPrivatePlan(false, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort)),
                    this::convertToTripRespDTO);
        }
        else{
            User user = validationUtil.validateUserExists(userEmail);
            if(validationUtil.validateUserIsAdmin(user.getUserId())) {
                //관리자: 전체 플랜 조회
                long totalCount = tripRepository.count();
                return PaginationService.getPagedData(page, size, totalCount,
                        pageable -> tripRepository.findAll(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort)),
                        this::convertToTripRespDTO);
            }
            else{
                //일반인: 타인의 공개 플랜 + 본인의 전체 플랜 조회
                long totalCount = tripRepository.countByPrivatePlan(false) + tripRepository.countByUser(user);
                return PaginationService.getPagedData(page, size, totalCount,
                        pageable -> tripRepository.findByPrivatePlanOrUser(false, user, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort)),
                        this::convertToTripRespDTO);
            }
        }
    }

    /**
     * 개별 여행 계획 조회
     * - 비공개 플랜이면 작성자만
     * - 공개 플랜이면 누구나
     */
    public TripItemRespDTO getSpecTrips(String userEmail, Integer tripId) {
        // 존재 여부 검증
        Trip trip = validationUtil.validateTripExists(tripId);

        // (A) 비공개 접근 권한 체크
        if (trip.getPrivatePlan()) {
            // 로그인 안 된 경우 → 에러
            if (userEmail == null) {
                throw new BadRequestExceptionMessage("비공개 여행 계획: 로그인 필요");
            }
            User currentUser = validationUtil.validateUserExists(userEmail);
            // 작성자와 다른 유저 → 에러
            if (!trip.getUser().getUserId().equals(currentUser.getUserId())) {
                throw new BadRequestExceptionMessage("비공개 여행 계획: 접근 권한 없음");
            }
        }
        // 공개 플랜이면 로그인 없이 접근 가능

        // (B) 도시 검색 횟수 증가
        City city = trip.getCity();
        city.setSearchCount(city.getSearchCount() + 1);
        cityRepository.save(city);

        // (F) 여행계획 좋아요 여부: 미로그인 시 무조건 false, 로그인 시 좋아요 여부 반환
        boolean isLiked = userEmail != null && tripLikeRepository.findByTripAndUser_Email(trip, userEmail).isPresent();

        // (G) DTO 생성
        return new TripItemRespDTO(convertToTripRespDTO(trip), isLiked);
    }

    /** 여행 계획 생성 (이미지 입력 제거됨) */
    public TripRespDTO writeTrip(String userEmail, String title, String startDate, String endDate,
                                 String privatePlan, Integer budget, Integer cityId) {
        User user = validationUtil.validateUserExists(userEmail);
        validationUtil.validateUserRestricted(user);

        LocalDate parsedStartDate = validationUtil.ValidateDate(startDate);
        LocalDate parsedEndDate = validationUtil.ValidateDate(endDate);
        if (!(Objects.equals(privatePlan, "yes") || Objects.equals(privatePlan, "no"))) {
            throw new BadRequestExceptionMessage("비공개 여부는 yes/no로 요청되어야 합니다.");
        }
        boolean isPrivate = Objects.equals(privatePlan, "yes");

        // cityId를 사용하여 City 엔티티 조회
        City city = validationUtil.validateCityExists(cityId);

        Trip trip = new Trip(user, title, isPrivate, parsedStartDate, parsedEndDate, budget, city);
        tripRepository.save(trip);

        return convertToTripRespDTO(trip);
    }

    /** 좋아요 리스트에 추가 */
    public TripLikeRespDTO addLikeTrip(String userEmail, Integer tripId) {
        User user = validationUtil.validateUserExists(userEmail);
        validationUtil.validateUserRestricted(user);
        Trip trip = validationUtil.validateTripExists(tripId);
        if (!trip.getUser().getUserId().equals(user.getUserId()) && trip.getPrivatePlan()) {
            throw new BadRequestExceptionMessage("접근 권한이 없습니다.");
        }
        if(trip.getUser().getUserId().equals(user.getUserId())) {
            throw new BadRequestExceptionMessage("본인의 여행계획은 좋아요할 수 없습니다.");
        } else if(tripLikeRepository.findByTripAndUser(trip, user).isPresent()) {
            throw new BadRequestExceptionMessage("이미 좋아요한 여행계획입니다.");
        }

        TripLike tripLike = new TripLike();
        tripLike.setTrip(trip);
        tripLike.setUser(user);
        tripLike.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        tripLikeRepository.save(tripLike);

        trip.setLikeCount(trip.getLikeCount() + 1);
        tripRepository.save(trip);

        return new TripLikeRespDTO(
                new MyTripRespDTO(convertToTripRespDTO(trip), trip.getPrivatePlan()),
                tripLike.getCreatedAt()
        );
    }

    /** 좋아요 리스트에서 삭제 */
    public boolean deleteLikeTrip(String userEmail, Integer tripId) {
        User user = validationUtil.validateUserExists(userEmail);
        Trip trip = validationUtil.validateTripExists(tripId);
        Optional<TripLike> tripLike = tripLikeRepository.findByTripAndUser(trip, user);
        if(tripLike.isEmpty()) {
            throw new BadRequestExceptionMessage("이미 좋아요 해제되어 있습니다.");
        }
        tripLikeRepository.delete(tripLike.get());
        trip.setLikeCount(trip.getLikeCount() - 1);
        tripRepository.save(trip);
        return true;
    }

    /** 좋아요 수가 높은 순으로 상위 10개의 공개된 여행 계획 조회 */
    public List<TripRespDTO> getTop10Trips() {
        List<Trip> topTrips = tripRepository.findTop10ByPrivatePlanFalseOrderByLikeCountDesc();
        return topTrips.stream()
                .map(this::convertToTripRespDTO)
                .collect(Collectors.toList());
    }

    /** 내 여행 계획 삭제 */
    public void deleteMyTrip(Integer tripId, String userEmail) {
        User user = validationUtil.validateUserExists(userEmail);
        validationUtil.validateUserRestricted(user);
        Trip trip = validationUtil.validateTripExists(tripId);
        if (!trip.getUser().getUserId().equals(user.getUserId())) {
            throw new BadRequestExceptionMessage("본인이 작성한 여행 계획만 삭제할 수 있습니다.");
        }
        imageService.getImageByTarget("trip", tripId)
                .ifPresent(img -> imageService.deleteImage(img.getImageId()));
        tripRepository.delete(trip);
    }

    /** 여행 계획 기본 정보 수정 (제목, 공개/비공개 여부 등) */
    public TripRespDTO updateTripBasicInfo(String userEmail, Integer tripId, String title, String isPrivate) {
        User user = validationUtil.validateUserExists(userEmail);
        validationUtil.validateUserRestricted(user);
        Trip trip = validationUtil.validateTripExists(tripId);

        // 작성자 또는 동반자인지 확인하는 로직 추가
        boolean isOwner = trip.getUser().getUserId().equals(user.getUserId());
        boolean isParticipant = trip.getParticipants() != null && trip.getParticipants().stream()
                .anyMatch(tp -> tp.getParticipant().getUserId().equals(user.getUserId()));
        if (!isOwner && !isParticipant) {
            throw new BadRequestExceptionMessage("여행 계획 수정 권한이 없습니다.");
        }

        boolean privatePlan;
        if (isPrivate.equalsIgnoreCase("yes")) {
            privatePlan = true;
        } else if (isPrivate.equalsIgnoreCase("no")) {
            privatePlan = false;
        } else {
            throw new BadRequestExceptionMessage("비공개 여부는 yes/no로 요청해주세요.");
        }
        trip.setTitle(title);
        trip.setPrivatePlan(privatePlan);
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);

        return convertToTripRespDTO(trip);
    }

    /**
     * 현재 사용자(userEmail)가 특정 tripId 여행 계획의 작성자인지 확인
     */
    public boolean isOwnerOfTrip(String userEmail, int tripId) {
        Trip trip = validationUtil.validateTripExists(tripId);
        User user = validationUtil.validateUserExists(userEmail);
        return trip.getUser().getUserId().equals(user.getUserId());
    }

    // 코드 중복을 없애기 위해 dto 변환 부분을 별도 분리함
    private TripRespDTO convertToTripRespDTO(Trip trip) {
        // 플랜 작성자의 프로필 이미지
        String userProfileUrl = imageService.getImageByTarget("userProfile", trip.getUser().getUserId())
                .map(img -> urlPrefix + img.getImageUrl())
                .orElse(null);

        // 플랜의 목적지 도시 대표 이미지
        String cityImageUrl = imageService.getImageByTarget("city", trip.getCity().getCityId())
                .map(img -> urlPrefix + img.getImageUrl())
                .orElse(null);

        // 플랜의 사용자 지정 대표 이미지
        String tripImageUrl = imageService.getImageByTarget("trip", trip.getTripId())
                .map(img -> urlPrefix + img.getImageUrl())
                .orElse(null);

        return new TripRespDTO(trip.getUser(), userProfileUrl, trip, cityImageUrl, tripImageUrl);
    }
}

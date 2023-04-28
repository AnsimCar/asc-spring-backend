package project.asc.AnsimCar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.asc.AnsimCar.domain.Account;
import project.asc.AnsimCar.domain.Address;
import project.asc.AnsimCar.domain.Rent;
import project.asc.AnsimCar.domain.UserCar;
import project.asc.AnsimCar.domain.type.Status;
import project.asc.AnsimCar.dto.rent.request.RentCreateRequest;
import project.asc.AnsimCar.dto.rent.request.RentSearchRequest;
import project.asc.AnsimCar.dto.rent.request.RentUpdateRequest;
import project.asc.AnsimCar.dto.rent.response.RentItemDetailResponse;
import project.asc.AnsimCar.dto.rent.response.RentResponse;
import project.asc.AnsimCar.exception.account.AccountNotFoundException;
import project.asc.AnsimCar.exception.rent.RentExistException;
import project.asc.AnsimCar.exception.rent.RentNotFoundException;
import project.asc.AnsimCar.exception.usercar.UserCarNotFoundException;
import project.asc.AnsimCar.exception.usercar.UserCarOwnerException;
import project.asc.AnsimCar.repository.AccountRepository;
import project.asc.AnsimCar.repository.AddressRepository;
import project.asc.AnsimCar.repository.RentRepository;
import project.asc.AnsimCar.repository.UserCarRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class RentService {

    private final UserCarRepository userCarRepository;
    private final AccountRepository accountRepository;
    private final RentRepository rentRepository;
    private final AddressRepository addressRepository;

    /**
     * 렌트 정보 저장
     */
    public void addRent(Long accountId, RentCreateRequest rentCreateRequest) {
        Account account = accountRepository.findById(accountId).orElseThrow(AccountNotFoundException::new);
        UserCar userCar = userCarRepository.findById(rentCreateRequest.getUserCarId()).orElseThrow(UserCarNotFoundException::new);
        Address address = addressRepository.save(rentCreateRequest.toAddressEntity());

        Optional<Rent> optional = rentRepository.findByUserCar_Id(userCar.getId());
        if (optional.isPresent()) {      //null 이 아닌 경우
            throw new RentExistException();
        } else {                        //null 인 경우
            Rent rent = Rent.builder()
                    .account(account)
                    .userCar(userCar)
                    .address(address)
                    .pricePerHour(rentCreateRequest.getPricePerHour())
                    .registrationDate(LocalDateTime.now())
                    .status(Status.AVAILABLE)
                    .build();

            userCar.updateUsable(false);

            rentRepository.save(rent);
        }
    }

    /**
     * id로 조회
     */
    public RentResponse findById(Long id) {
        return RentResponse.from(rentRepository.findById(id).orElseThrow(RentNotFoundException::new));
    }

    /**
     * id로 모두 조회
     */
    public RentResponse findInfoById(Long id) {
        return RentResponse.from(rentRepository.findById(id).orElseThrow(RentNotFoundException::new));
    }

    /**
     * 계정Id로 렌트 조회
     */
    //TODO 추후에 사용자가 자신이 등록한 렌트를 확인하는 뷰를 개발할 때 렌트 정보만 띄울지, 차 + 렌트 정보를 같이 띄울지 고민해서 수정이 필요할 것 같다.
    public List<RentResponse> findByUserId(Long accountId) {
        List<Rent> rents = rentRepository.findByAccount_Id(accountId);

        return rents.stream().map(RentResponse::from).toList();
    }

    /**
     * 렌트 상태 수정
     */
    public void updateRentStatus(Long accountId, Long rentId, RentUpdateRequest rentUpdateRequest) {
        Rent rent = rentRepository.findById(rentId).orElseThrow(RentNotFoundException::new);
        validateOwner(accountId, rent);

        rent.updateStatus(rentUpdateRequest);
    }

    /**
     * 렌트 대여, 반납 날짜 업데이트
     */
    public void updateRentalReturnDate(Long accountId, Long rentId, RentUpdateRequest rentUpdateRequest) {
        Rent rent = rentRepository.findById(rentId).orElseThrow(RentNotFoundException::new);
        validateOwner(accountId, rent);

        rent.updateRentalReturnDate(rentUpdateRequest);
        UserCar userCar = userCarRepository.findById(rent.getUserCar().getId()).orElseThrow(UserCarNotFoundException::new);

        userCar.updateUsable(true);
    }

    /**
     * 렌트 삭제
     */
    public void deleteRent(Long accountId, Long rentId) {
        Rent rent = rentRepository.findById(rentId).orElseThrow(RentNotFoundException::new);
        validateOwner(accountId, rent);

        rentRepository.deleteById(rentId);
    }

    public void validateOwner(Long accountId, Rent rent) {
        if (!rent.isOwner(accountId)) {
            throw new UserCarOwnerException();
        }
    }

    /**
     * 모든 렌트카 조회(페이징)
     */
    @Transactional(readOnly = true)
    public Page<RentItemDetailResponse> findByAvailable(Pageable pageable) {

        return rentRepository.findByStatus(Status.AVAILABLE, pageable).map(RentItemDetailResponse::from);

    }

    /**
     * 렌트 조건 검색
     */
    @Transactional(readOnly = true)
    public Page<RentItemDetailResponse> findAllComplex(RentSearchRequest request, Pageable pageable) {

        return rentRepository.findAllComplex(request, pageable).map(RentItemDetailResponse::from);

    }
}

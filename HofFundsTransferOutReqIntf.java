package com.jpmc.kcg.hof.biz;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.ibm.msg.client.jakarta.wmq.WMQConstants;
import com.jpmc.kcg.com.biz.BizCom;
import com.jpmc.kcg.com.biz.ComPshMsgBean;
import com.jpmc.kcg.com.biz.ReleaseValidation;
import com.jpmc.kcg.com.constants.ComConst;
import com.jpmc.kcg.com.dao.ComTrHoldLDao;
import com.jpmc.kcg.com.dto.ComAcctAlarmIn;
import com.jpmc.kcg.com.dto.ComTrHoldL;
import com.jpmc.kcg.com.dto.ComTrHoldLIn;
import com.jpmc.kcg.com.enums.BizDvsnCdEnum;
import com.jpmc.kcg.com.enums.HoldRsnCdEnum;
import com.jpmc.kcg.com.enums.NumberingEnum;
import com.jpmc.kcg.com.enums.SvcHourStsCdEnum;
import com.jpmc.kcg.com.enums.SvcTmDvsnCdEnum;
import com.jpmc.kcg.com.enums.TrStsCdEnum;
import com.jpmc.kcg.com.enums.TrmnlDvsnCdEnum;
import com.jpmc.kcg.com.exception.InternalResponseException;
import com.jpmc.kcg.com.utils.DateFormat;
import com.jpmc.kcg.frw.FrwContext;
import com.jpmc.kcg.frw.FrwDestination;
import com.jpmc.kcg.frw.FrwServiceBean;
import com.jpmc.kcg.frw.FrwTemplate;
import com.jpmc.kcg.frw.VOUtils;
import com.jpmc.kcg.hof.biz.vo.KftHof0200300000;
import com.jpmc.kcg.hof.biz.vo.KftHof0200400000;
import com.jpmc.kcg.hof.biz.vo.KftHof0210300000;
import com.jpmc.kcg.hof.biz.vo.LvbHof0200400000;
import com.jpmc.kcg.hof.biz.vo.LvbHof0210400000;
import com.jpmc.kcg.hof.dao.HbkTrLDao;
import com.jpmc.kcg.hof.dao.HofLrgAmtSplitMMapper;
import com.jpmc.kcg.hof.dao.HofTrLDao;
import com.jpmc.kcg.hof.dto.HbkTrL;
import com.jpmc.kcg.hof.dto.HofLrgAmtSplitM;
import com.jpmc.kcg.hof.dto.HofLrgAmtSplitTrL;
import com.jpmc.kcg.hof.dto.HofLrgAmtSplitTrLSumOut;
import com.jpmc.kcg.hof.dto.HofTrL;
import com.jpmc.kcg.hof.dto.SelectHofTrListByHostNoDaoIn;
import com.jpmc.kcg.hof.dto.UpdateHofTrKeyIn;
import com.jpmc.kcg.hof.enums.FundTypeEnum;
import com.jpmc.kcg.hof.enums.HbkConst;
import com.jpmc.kcg.hof.enums.HofConst;
import com.jpmc.kcg.hof.enums.HofRespCdEnum;

import lombok.extern.slf4j.Slf4j;

/**
 * 2024.07.19 한성흔 
 * 타행이체 당발요청 (0200/400000)
 */
@Slf4j
@Component
public class HofFundsTransferOutReqIntf extends FrwServiceBean<LvbHof0200400000> {

	@Autowired
	private FrwTemplate frwTemplate;
	@Autowired
	private FrwContext frwContext;
	@Autowired
	private HofCom hofCom;
	@Autowired
	private HofTrLDao hofTrLDao;
	@Autowired
	private HbkCom hbkCom;
	@Autowired
	private BizCom bizCom;
	@Autowired
	private ReleaseValidation releaseValidation;
	@Autowired
	private ConversionService conversionService;
	@Autowired
	private HbkFundsTransferBeneficiaryInquiryOutReqIntf hbkFundsTransferBeneficiaryInquiryOutReq;
	@Autowired
	private ComPshMsgBean comPshMsgBean;
	@Autowired
	private HofLrgAmtSplitMMapper hofLrgAmtSplitMMapper;
	@Autowired
	private ComTrHoldLDao comTrHoldLDao;
	@Autowired
	private HbkTrLDao hbkTrLDao;

	/**
	 * 전문 검증 및 제어
	 */
	@Override
	public boolean control(LvbHof0200400000 in) {
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
//		int fldNo = in.validate();
//		if (0 < fldNo) {
//			frwTemplate.send(FrwDestination.LVB_HOF, ComUtils.getFldErrTlg(frwContext.getTlgCtt(), fldNo));
//			return false;
//		}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
		// 전문 format validation check
		if (in.validate() > 0) {
			String fieldNo = String.valueOf(in.validate() + 1);
			String errCd = StringUtils.leftPad(fieldNo, 3, ComConst.CHAR_0); // 000 ~ 필드번호가 응답코드로 리턴.
			throw new InternalResponseException(errCd);
		}

		// 은행이 협업은행인지 확인
		List<BizDvsnCdEnum> bizEnumList = new ArrayList<BizDvsnCdEnum>();
		bizEnumList.add(BizDvsnCdEnum.HOF);

		if (!bizCom.getBankCodeValidation(in.getBeneficiaryBankCode(), bizEnumList)) {
			throw new InternalResponseException(HofRespCdEnum.RESP_CD_008.getCode());
		}

		//농협 포함 대표은행코드로 재설정
		String kftcBnkCd = bizCom.getNHAcctBnkCd(in.getBeneficiaryBankCode(),
				in.getBeneficiaryAccountNumber());
		in.setBeneficiaryBankCode(kftcBnkCd);

		// 전문시간 현재시간과 비교 체크.
		if (!in.getSystemSendReceiveTime().format(DateTimeFormatter.BASIC_ISO_DATE)
				.equals(frwContext.getSysTmstmp().format(DateTimeFormatter.BASIC_ISO_DATE))) {
			throw new InternalResponseException(HofRespCdEnum.RESP_CD_002.getCode());
		}

		// 전문시간 현재시간과 비교 체크.
		if (!in.getTransactionDate().format(DateTimeFormatter.BASIC_ISO_DATE)
				.equals(frwContext.getSysTmstmp().format(DateTimeFormatter.BASIC_ISO_DATE))) {
			throw new InternalResponseException(HofRespCdEnum.RESP_CD_009.getCode());
		}

		if (ComConst.JPMC_BANK_CD.equals(in.getBeneficiaryBankCode())) {
			// 개설은행이 당행일때 에러.
			throw new InternalResponseException(HofRespCdEnum.RESP_CD_304.getCode());
		}
		
		// 은행상태가 개시(은행상태 응답코드 :000)가 아닌 경우는 거절응답을 반환한다.
		String bankConnectStatus = bizCom.getConnectivtyValidation(ComConst.JPMC_BANK_CD,
				BizDvsnCdEnum.HOF.getValue(), ComConst.HST);

		if (!bankConnectStatus.equals(HofRespCdEnum.RESP_CD_000.getCode())) {
			if (bankConnectStatus.equals("911")
					|| bankConnectStatus.equals("916")
					|| bankConnectStatus.equals("917")) {  // 종료(예고)일때 오는게 123, 113, 143 (911,916,917)
				throw new InternalResponseException(bankConnectStatus);
			}
		}
		
		//홀드거래일 경우 중복방지를 위해 HOST_NO & TR_UNQ_NO 조회조건으로 DB LOCK
		String holdingRsn = frwContext.getTlgHdr().get(ComConst.HOLD_RSN_CD);
		if (!StringUtils.isEmpty(holdingRsn)) {
			ComTrHoldL comTrHoldLIn = new ComTrHoldL();
			comTrHoldLIn.setBizDvsnCd(BizDvsnCdEnum.HOF.getValue());
			comTrHoldLIn.setHoldStsCd(HofConst.CHAR_01);
			comTrHoldLIn.setHoldRsnCd(holdingRsn);
			comTrHoldLIn.setOutinDvsnCd(HofConst.CHAR_01);
			comTrHoldLIn.setHostNo(in.getMsgNo());
			comTrHoldLIn.setTrUnqNo(in.getTransactionIdNumber());
			
			comTrHoldLDao.selectTransactionReleaseForUpdate(comTrHoldLIn);
			
			log.debug("#########[SELECT] 중복방지 COM_TR_HOLD 테이블 LOCK, hostNo : {}", in.getMsgNo());
		}
		
		//HBK불능거래인지 확인
		String hbkErrTrYn = frwContext.getTlgHdr().get(HofConst.HBK_ERR_TR_YN);
		log.debug("#########[HBK 불능거래YN] hbkErrTrYn: {}", hbkErrTrYn);
		
		// 재처리 전문 아니면 hold
		if (!in.getMessageType().endsWith(ComConst.CHAR_1) && _getHofLrgAmtSplitM(in) == null && !ComConst.Y.equals(hbkErrTrYn)) { 	// 2025.04.08 단 건인 경우만 체크
			//통신망 개시 상태인지 확인
			boolean isNotConnected = releaseValidation.getReleaseConnectivityStatus(
					ComConst.JPMC_BANK_CD, BizDvsnCdEnum.HOF.getValue());
			//서비스 개시 상태인지 확인
			String svsHourCd = releaseValidation.getReleaseServiceHour(
					SvcTmDvsnCdEnum.HOF_OUTBOUND_SERVICE_HOUR.getValue(), LocalDateTime.now());
			// 특정 계좌의 시작시간 체크 
			boolean holdAcctOpn = releaseValidation.getReleaseAccount(in.getWithdrawalAccountNumber(), in.getMsgNo());

			// 통신망 미개시 + 서비스 미개시 + 계좌 미개시 경우
			if (isNotConnected || !SvcHourStsCdEnum.SERVICE_HOUR.getCode().equals(svsHourCd)
					|| holdAcctOpn) {
				String holdRsnCd = holdAcctOpn ? HoldRsnCdEnum.ACCOUNT_SERVICE_HOUR.getCode()
						: isNotConnected ? HoldRsnCdEnum.CONNECTIVITY.getCode()
								: SvcHourStsCdEnum.SERVICE_HOUR_BEFORE.getCode().equals(svsHourCd)
										? HoldRsnCdEnum.KCG_SERVICE_HOUR.getCode()
										: "";

				//대기거래로 입력
				_createHoldTransaction(in, holdRsnCd);
				return false;
			}
		}

		// 금액이 0인경우
		if (in.getBeneficiaryAmount() == 0) {
			throw new InternalResponseException(HofRespCdEnum.RESP_CD_407.getCode());
		}

		// 수취계좌 = 출금계좌
		if (in.getBeneficiaryBankCode().equals(in.getRequestBank())
				&& in.getBeneficiaryAccountNumber().equals(in.getWithdrawalAccountNumber())) {
			throw new InternalResponseException(HofRespCdEnum.RESP_CD_308.getCode());
		}

		return super.control(in);

	}

	@Override
	public void process(LvbHof0200400000 in) {
		// 디버그 모드에서만 로그를 출력
		if (log.isDebugEnabled()) {
			log.debug("process Input Value :::: {}", in);
		}
		
		String trUnqNo= "";

		// 1. 원본 거래 금액을 가져옴
		Long orgnTrAmt = in.getBeneficiaryAmount();

		// 2. 거래 금액이 거액거래인지 체크 (원본 거래 금액이 거액거래 기준 이상인지 확인)
		boolean isLargAmtTr = orgnTrAmt > HofConst.LARGE_AMT;
		
		// 3. 거액거래일 경우 
		if (isLargAmtTr) {
			/*
			 *  HBK불능거래 체크 (불능일경우 HOFI거래 요청)
			 *  1. HBK불능 직접호출 
			 *  2. 순이체한도 대기거래 release -> HBK원장 거래 존재 유무 확인
			 */
			HbkTrL dupIn = new HbkTrL();
			dupIn.setTrDt(in.getTransactionDate().format(DateFormat.YYYYMMDD.getFormatter()));
			dupIn.setOutinDvsnCd(HbkConst.OUTIN_DVSN_CD_01);
			dupIn.setHostNo(in.getMsgNo()); // HOF 이체거래 HOST_NO

			int countHbk = hbkTrLDao.countHbkTransaction(dupIn);
			log.debug("#########[HBK 거래 유무] countHbk: {}", countHbk);
			
			// 3-1. HBK 커넥션 확인 + hbk불능 거래 아닌 경우
			if (countHbk == 0 && hbkCom.checkHbkConnection(HbkConst.TR_DVSN_450000, in.getBeneficiaryBankCode())) {
				
				try {
					ComTrHoldLIn comTrHoldLIn = new ComTrHoldLIn();
					comTrHoldLIn.setHostNo(in.getMsgNo());
					bizCom.setComHoldStatusDone(comTrHoldLIn);
					log.debug("#########[UPDATE] 중복방지 COM_TR_HOLD 테이블 LOCK 해제, hostNo : {}", in.getMsgNo());
					
				} catch (Exception e) {
					log.error("isLargAmtTr INNER COMMIT EXCEPTION CATCH", e);
				}
				
				// HBK 호출
				hbkFundsTransferBeneficiaryInquiryOutReq.hbkFundsTransferBeneficiaryInquiryOutReq(in);
			}
			//3-1. HBK 커넥션이 안된 경우
			else {
				if (releaseValidation.getReleaseLrgAmtNetDebitCap(true, new BigDecimal(in.getBeneficiaryAmount()))) {	// true - 순이체한도 현재금액 저장
					
					// 3-1-1.순이체한도에 포함되면 거래 보류 처리
					_createHoldTransaction(in, HoldRsnCdEnum.NET_DEBIT_CAP.getCode());
					return;
				}
				
				// 거액 HOF거래 시 수취조회 선행
				_checkLargBeneInquiry(in);
				
				try {
					ComTrHoldLIn comTrHoldLIn = new ComTrHoldLIn();
					comTrHoldLIn.setHostNo(in.getMsgNo());
					bizCom.setComHoldStatusDone(comTrHoldLIn);
					log.debug("#########[UPDATE] 중복방지 COM_TR_HOLD 테이블 LOCK 해제, hostNo : {}", in.getMsgNo());
					
				} catch (Exception e) {
					log.error("isLargAmtTr INNER COMMIT EXCEPTION CATCH", e);
				}
				
				// 수취조회 후 수취인명 셋
				String tlgCtt =  VOUtils.toString(in);
				
				//3-1-2.아니라면 거액거래 테이블에 쪼개서 입력
				hofCom.createLrgAmtSplitTransaction(
						in.getTransactionDate().format(DateFormat.YYYYMMDD.getFormatter()),
						in.getMsgNo(), in.getBeneficiaryAmount(), tlgCtt);
			}
			return;
			
		} else {
			
			// 4. 현재시검 기준 거액거래가 아닌 경우, 거액분할원장 정보 조회 (이미 분할된 거래인지 확인)
			HofLrgAmtSplitM lrgTrM = _getHofLrgAmtSplitM(in);
			log.debug("#########거액분할처리 여부 : {}", lrgTrM);
			// 5. 거액이라 이미 분할된거래 처리
			if (lrgTrM == null) {
				
				// 6. 거액거래가 아닌 경우, 거래 금액이 순이체한도에 포함되는지 체크
				// 2025.04.08 거액분할인 경우 체크하지 않기 위해
				if (releaseValidation
						.getReleaseNetDebitCap(new BigDecimal(in.getBeneficiaryAmount()))) {
					
					// 순이체한도에 포함되면 거래 보류 처리
					_createHoldTransaction(in, HoldRsnCdEnum.NET_DEBIT_CAP.getCode());
					return;
				}
				
				/**
				 * HOST번호로 요청한 거래가 있는지 확인 -> 중복거래 방지
				 */
				SelectHofTrListByHostNoDaoIn selectIn = new SelectHofTrListByHostNoDaoIn();
				selectIn.setTrDt(in.getTransactionDate().format(DateFormat.YYYYMMDD.getFormatter()));
				selectIn.setHostNo(in.getMsgNo());
				selectIn.setOutinDvsnCd(HofConst.OUTIN_DVSN_CD_01);
				List<HofTrL> reqTrInfo = hofTrLDao.selectOrgnTrListByHostNo(selectIn);
				 
				if(!CollectionUtils.isEmpty(reqTrInfo)) {
					ComTrHoldLIn comTrHoldLIn = new ComTrHoldLIn();
					comTrHoldLIn.setHostNo(in.getMsgNo());
					comTrHoldLIn.setRespCd(HofRespCdEnum.RESP_CD_998.getCode());
					bizCom.setComHoldStatusDone(comTrHoldLIn);
					log.debug("#########[UPDATE] 중복방지 COM_TR_HOLD 테이블 LOCK 해제, hostNo : {}", in.getMsgNo());
					
					try {
						Map<String, Object> errorValList = new Hashtable<>();
						String errorMessage = String.format(
								"[I N F O] HOFI : DUPLICATE TRANSACTION [DATE=%s][HOST_NO=%s][KFTC_NO=%s]",
								LocalDate.now().format(DateFormat.YYYYMMDD.getFormatter()),
								reqTrInfo.get(0).getHostNo(), reqTrInfo.get(0).getTrUnqNo());
						errorValList.put("errTlgId", HofConst.DUP_TR_BLOCK_TITLE);
						errorValList.put("errCtt", errorMessage);
						comPshMsgBean.sendAlarm(8, errorValList.get("errTlgId").toString(), errorValList,
								null, true);
					}catch (Exception e2) {
						log.error("Message Request Fail", e2);
					}
					
					return;
				}
				
				// 분할된거래가 아니면 일반거래 처리 진행
				trUnqNo = _processTransaction(in);
			} else if (TrStsCdEnum.REQUEST.getTrStsCd().equals(lrgTrM.getTrStsCd())) {
				log.debug(": Y ######### {}", lrgTrM.getTrStsCd());
				
				/**
				 * HOST번호&trUnqNo로 요청한 거래가 있는지 확인 -> 중복거래 방지
				 */
				SelectHofTrListByHostNoDaoIn selectIn = new SelectHofTrListByHostNoDaoIn();
				selectIn.setTrDt(in.getTransactionDate().format(DateFormat.YYYYMMDD.getFormatter()));
				selectIn.setHostNo(in.getMsgNo());
				selectIn.setTrUnqNo(in.getTransactionIdNumber());
				selectIn.setOutinDvsnCd(HofConst.OUTIN_DVSN_CD_01);
				List<HofTrL> reqTrInfo = hofTrLDao.selectOrgnTrListByHostNo(selectIn);
				 
				if(!CollectionUtils.isEmpty(reqTrInfo)) {
					ComTrHoldLIn comTrHoldLIn = new ComTrHoldLIn();
					comTrHoldLIn.setHostNo(in.getMsgNo());
					comTrHoldLIn.setSelTrUnqNo(in.getTransactionIdNumber());
					comTrHoldLIn.setRespCd(HofRespCdEnum.RESP_CD_998.getCode());
					bizCom.setComHoldStatusDone(comTrHoldLIn);
					log.debug("#########[UPDATE] 중복방지 COM_TR_HOLD 테이블 LOCK 해제, hostNo : {}", in.getMsgNo());
					
					try {
						// 에러 메시지 문자열 생성
						Map<String, Object> errorValList = new Hashtable<>();
						String errorMessage = String.format(
								"[I N F O] HOFI : DUPLICATE TRANSACTION [DATE=%s][HOST_NO=%s][KFTC_NO=%s]",
								LocalDate.now().format(DateFormat.YYYYMMDD.getFormatter()),
								in.getMsgNo(), in.getTransactionIdNumber());
						errorValList.put("errTlgId", HofConst.DUP_TR_BLOCK_TITLE);
						errorValList.put("errCtt", errorMessage);
						comPshMsgBean.sendAlarm(8, errorValList.get("errTlgId").toString(), errorValList,
								null, true);
					}catch (Exception e2) {
						log.error("Message Request Fail", e2);
					}
					
					return;
				}
				
				// 진행해야하는 , 트랜잭션을 진행
				trUnqNo = _processLargSplitTransaction(in, lrgTrM.getSplitOrgnTrTotAmt());
			} else {
				log.debug(": N ######### {}", lrgTrM.getTrStsCd());
				return;
			}
		}

		// 7. 거래 완료 후, 비즈니스 컴포넌트에서 거래 완료 상태 업데이트
		try {
			ComTrHoldLIn comTrHoldLIn = new ComTrHoldLIn();
			comTrHoldLIn.setHostNo(in.getMsgNo());
			comTrHoldLIn.setSelTrUnqNo(in.getTransactionIdNumber());	// 거액분할 시 세팅됨 (단 건은 null)
			comTrHoldLIn.setUpTrUnqNo(trUnqNo);   
			bizCom.setComHoldStatusDone(comTrHoldLIn);
			log.debug("#########[UPDATE] 중복방지 COM_TR_HOLD 테이블 LOCK 해제, hostNo : {}", in.getMsgNo());
			
		} catch (Exception e) {
			log.error("process INNER COMMIT EXCEPTION CATCH", e);
		}
	}
	
	private HofLrgAmtSplitM _getHofLrgAmtSplitM(LvbHof0200400000 in) {
		
		return hofLrgAmtSplitMMapper.selectByPrimaryKey(
				in.getTransactionDate().format(DateFormat.YYYYMMDD.getFormatter()),
				in.getMsgNo());
	}
	
	/**
	 * 단 건 거래 프로세스 정의
	 * HOF_TR_L 신규, KFT_QUE SEND
	 * @param in
	 * @param isLargAmtTr
	 * @param orgnAmt
	 */
	private String _processTransaction(LvbHof0200400000 in) {

		//출금인정보 재설정
		String whdrwl = _getSenderInfo(in);
		
		// 거래번호 채번(1)
		String tlgTrceNo = bizCom.getHofOracleSeqNo(NumberingEnum.HOFKFT01);
		String trUnqId = ComConst.JPMC_BANK_CD.concat(ComConst.CHAR_00).concat(tlgTrceNo);
		
		if (Integer.valueOf(StringUtils.isEmpty(in.getChannelType()) ? ComConst.CHAR_00
				: in.getChannelType()) < 46) {		// 수취계좌조회 선행하지 않는 경우.
			
			/**
			 * HOF_TR_L 원장 등록 공통 메소드 호출
			 */
			_insertHofTransaction(in, false, null, trUnqId, tlgTrceNo, whdrwl);
			
		} else { // 수취계좌조회 선행하는 경우.
			// 수취계좌전문 전송. 거래번호(1)을 사용함.
			_checkBeneInquiry(in, trUnqId);

			// 거래번호(2) 채번
			String afterTlgTrceNo = bizCom.getHofOracleSeqNo(NumberingEnum.HOFKFT01);
			String afterTrUnqId = ComConst.JPMC_BANK_CD.concat(ComConst.CHAR_00)
					.concat(afterTlgTrceNo);

			UpdateHofTrKeyIn updateIn = new UpdateHofTrKeyIn();
			updateIn.setTlgKndDvsnCd(in.getMessageType());
			updateIn.setTlgTrDvsnCd(in.getMessageCode());
			updateIn.setTrDt(in.getTransactionDate().format(DateFormat.YYYYMMDD.getFormatter()));
			updateIn.setTrUnqNo(trUnqId); // 거래번호(1)값으로 조회
			updateIn.setAfterHofTlgTrceNo(afterTlgTrceNo); // 거래번호(2) update
			updateIn.setAfterTrUnqNo(afterTrUnqId); // 거래번호(2) update
			updateIn.setTrStsCd(TrStsCdEnum.REQUEST.getTrStsCd());
			updateIn.setTlgTrDvsnCd(in.getMessageCode());
			// 입금거래에 대핸 새로운 정보 update
			hofTrLDao.updateHofTransactionKey(updateIn);
			tlgTrceNo = afterTlgTrceNo;
			trUnqId = afterTrUnqId;
		}

		/**
		 * 0200400000 송수신
		 */
		_sendAndRcv0200400000(in, trUnqId, tlgTrceNo, whdrwl);
		
		return trUnqId;
	}
	
	/**
	 * HOF_TR_L 신규, KFT_QUE SEND
	 * @param in
	 * @param isLargAmtTr
	 * @param orgnAmt
	 */
	private String _processLargSplitTransaction(LvbHof0200400000 in, BigDecimal orgnAmt) {
		
		//출금인정보 재설정
		String whdrwl = _getSenderInfo(in);
		
		// 거액거래는 거래번호(1) 무시하고 거액테이블 입력시 채번된 번호 사용
		String trUnqId = in.getTransactionIdNumber();
		String tlgTrceNo = trUnqId.substring(5);
		
		/**
		 * HOF_TR_L 원장 등록 공통 메소드 호출
		 */
		_insertHofTransaction(in, true, orgnAmt, trUnqId, tlgTrceNo, whdrwl);
		
		/**
		 * 0200400000 송수신
		 */
		_sendAndRcv0200400000(in, trUnqId, tlgTrceNo, whdrwl);
		
		return trUnqId;
	}

	/**
	 * 단 건 거래 시 수취계좌 조회를 수행 
	 * 수취계좌조회가 50초내에 정상응답이 아닌 경우에는 에러종료한다.
	 * @param in
	 * @param beneTrUnqId
	 * @param orgnAmt
	 */
	private void _checkBeneInquiry(LvbHof0200400000 in, String beneTrUnqId) {
		
		String whdrwl = _getSenderInfo(in);
		
		// 트랜잭션 분리 서비스 호출
		try {
			/**
			 * 0200300000 송수신
			 */
			KftHof0210300000 respVo = _sendAndRcv0200300000(in, beneTrUnqId);
			
			if (StringUtils.isNotEmpty(respVo.getResponseCode())) {
				if (HofRespCdEnum.RESP_CD_000.getCode().equals(respVo.getResponseCode())) {
					HofTrL hofTrL = new HofTrL();
					hofTrL.setTrDt(
							in.getTransactionDate().format(DateFormat.YYYYMMDD.getFormatter()));
					hofTrL.setOutinDvsnCd(HofConst.OUTIN_DVSN_CD_01);
					//					hofTrL.setTlgKndDvsnCd(HofConst.TLG_KND_DVSN_NO_0200);
					hofTrL.setTlgTrDvsnCd(HofConst.TR_DVSN_300000);
					hofTrL.setOpnBnkCd(in.getBeneficiaryBankCode());
					hofTrL.setTrStsCd(TrStsCdEnum.REQUEST.getTrStsCd());
					hofTrL.setHostNo(in.getMsgNo());
					hofTrL.setHofTlgTrceNo(beneTrUnqId.substring(5));
					hofTrL.setTrUnqNo(beneTrUnqId);
					hofTrL.setTrmnlDvsnCd(TrmnlDvsnCdEnum.LVB.getCode());
					hofTrL.setStrtChnlDvsnCd(in.getChannelType());
					hofTrL.setTrcId(frwContext.getOrgnTractId());
					hofTrL.setRqerNm(whdrwl);
					hofTrL.setRqerNm2(in.getSenderName());
					hofTrL.setSndrRealNm(in.getSenderName());
					hofTrL.setWhdrwlAcctNo(in.getWithdrawalAccountNumber()); //출금계좌번호
					if (hbkCom.checkSpAcctYn(in.getWithdrawalAccountNumber())) {
						hofTrL.setFnncFrdInfoCd(HofConst.FRAUD_SP);
					}
					hofTrL.setTotTrAmt(new BigDecimal(in.getBeneficiaryAmount()));
					hofTrL.setTrUnqNo(beneTrUnqId); //key
					//					hofTrL.setFnncFrdInfoCd(respVo.getSuspectedFraudInfo());
					hofTrL.setRespCd(respVo.getResponseCode());
					hofTrL.setTlgKndDvsnCd(HofConst.TLG_KND_DVSN_NO_0210);
					//					hofTrL.setTlgTrDvsnCd(HofConst.TR_DVSN_400000);
					hofTrL.setRcpntNm(respVo.getBeneficiaryName()); //수취인명 update
					hofTrL.setNoteExCd(respVo.getBeneficiaryAreaCode()); //교환소코드
					if (StringUtils.isNotEmpty(respVo.getBeneficiaryBranchCode())) {
						hofTrL.setOpnBnkBrnchCd(respVo.getBeneficiaryBranchCode().substring(3));
					}

					// log정보를 바탕으로 수취계좌조회 거래내역으로 등록 (1)
					hofCom.insertHofTransaction(hofTrL);

					// 수취조회 결과로 설정
					in.setBeneficiaryName(respVo.getBeneficiaryName());//수취인명 update
					
				} else {
					in.setMessageCode(HofConst.TR_DVSN_300000);
					in.setTransactionIdNumber(respVo.getTransactionIdNumber());
					throw new InternalResponseException(respVo.getResponseCode());
				}
			} else {
				in.setTransactionIdNumber(beneTrUnqId);
				throw new InternalResponseException(HofRespCdEnum.RESP_CD_902.getCode());
			}
		} catch (Exception e) {
			log.error(ExceptionUtils.getRootCauseMessage(e), e);
			if (!(e instanceof InternalResponseException)) {
				in.setTransactionIdNumber(beneTrUnqId);
				throw new InternalResponseException(HofRespCdEnum.RESP_CD_902.getCode());
			} else {
				throw new InternalResponseException(e.getMessage());
			}
		}
	}
	
	/**
	 * 거액대상 거래 시 수취계조조회를 수행하여 불능 시 거액분할 하지 않는다. 
	 * 수취계좌조회가 50초내에 정상응답이 아닌 경우에는 에러종료한다.
	 * @param in
	 * @param beneTrUnqId
	 * @param isLargAmtTr
	 */
	private void _checkLargBeneInquiry(LvbHof0200400000 in) {
		// 수취계좌조회 선행
		if (Integer.valueOf(StringUtils.isEmpty(in.getChannelType()) ? ComConst.CHAR_00
				: in.getChannelType()) >= 46) {
			
			// 거래번호 채번
			String tlgTrceNo = bizCom.getHofOracleSeqNo(NumberingEnum.HOFKFT01);
			String beneTrUnqId = ComConst.JPMC_BANK_CD.concat(ComConst.CHAR_00).concat(tlgTrceNo);
			
			// 트랜잭션 분리 서비스 호출
			try {
				/**
				 * 0200300000 송수신
				 */
				KftHof0210300000 respVo = _sendAndRcv0200300000(in, beneTrUnqId);
				
				/**
				 * 불능 시 InternalResponseException throw 하여 응답
				 */
				if (StringUtils.isNotEmpty(respVo.getResponseCode())) {
					if (!HofRespCdEnum.RESP_CD_000.getCode().equals(respVo.getResponseCode())) {
						in.setMessageCode(HofConst.TR_DVSN_300000);
						in.setTransactionIdNumber(respVo.getTransactionIdNumber());
						throw new InternalResponseException(respVo.getResponseCode());
					} 
					// 정상
					else {
						in.setBeneficiaryName(respVo.getBeneficiaryName());//수취인명 update
					}
				} else {
					in.setTransactionIdNumber(beneTrUnqId);
					throw new InternalResponseException(HofRespCdEnum.RESP_CD_902.getCode());
				}
			} catch (Exception e) {
				log.error(ExceptionUtils.getRootCauseMessage(e), e);
				if (!(e instanceof InternalResponseException)) {
					in.setTransactionIdNumber(beneTrUnqId);
					throw new InternalResponseException(HofRespCdEnum.RESP_CD_902.getCode());
				} else {
					throw new InternalResponseException(e.getMessage());
				}
			}
		}
	}
	
	/**
	 * 당발 수취조회 송수신 (020000000)
	 * @param in
	 * @param trUnqNo
	 * @return
	 */
	private KftHof0210300000 _sendAndRcv0200300000(LvbHof0200400000 in, String trUnqNo) {
		
		String whdrwl = _getSenderInfo(in);
		
		// 수취조회 전문송신을 위한 전문관리 항목들 조립
		KftHof0200300000 kftIn = conversionService.convert(in, KftHof0200300000.class);
		kftIn.setMessageTransmissionDate(LocalDate.now()); /* 전문전송일 */
		kftIn.setMessageSendTime(LocalTime.now()); /* 전문전송시간 */
		kftIn.setTraxOccurredDate(LocalDate.now()); /* 거래발생일 */
		kftIn.setMessageTrackingNumber(trUnqNo.substring(5)); /* 전문추적 관리번호 */
		kftIn.setTransactionIdNumber(trUnqNo); /* 거래고유번호*/
		kftIn.setHandlingInstitutionRepCode(ComConst.JPMC_BANK_CD);
		kftIn.setRequestBranchCode(ComConst.JPMC_BANK_CD + ComConst.JPMC_BRNCH_CD);
		kftIn.setMediaType(in.getMediaType()); /* 기본은 매체구분 : 06 기타 / KIB는 들어오는 대로*/
		kftIn.setWithdrawalAccountNumber(in.getWithdrawalAccountNumber()); /* 출금계좌번호 */
		kftIn.setWithdrawer(whdrwl);
		if(in.getBeneficiaryAmount() > HofConst.LARGE_AMT) { //거액일 경우 
			kftIn.setTransactionAmount(HofConst.LARGE_AMT);
		}
		
		log.info(
				">>>>>>>>>>>>>>FundsTransfer Out Request ::  Beneficiary Account Inquiry Start<<<<<<<<<<<<<");
		// send를 해야 frw_log정보가 생기기 때문에 , send를 먼저 한다.
		KftHof0210300000 respVo = frwTemplate.sendAndReceive(FrwDestination.KFT_HOF, kftIn,
				KftHof0210300000.class, Duration.ofSeconds(50L));
		log.info(
				">>>>>>>>>>>>>> FundsTransfer Out Request ::  Beneficiary Account Inquiry Success <<<<<<<<<<<<<");
		
		return respVo;
	}
	
	/**
	 * 당발 타행이체 송수신 (0200400000)
	 * @param in
	 * @param trUnqNo
	 * @param tlgTrceNo
	 * @param whdrwl
	 */
	private void _sendAndRcv0200400000(LvbHof0200400000 in, String trUnqNo, String tlgTrceNo, String whdrwl) {
		
		// LVB전문 -> KFTC 전문 변환
		KftHof0200400000 kftIn = conversionService.convert(in, KftHof0200400000.class);
		if (hbkCom.checkSpAcctYn(in.getWithdrawalAccountNumber())) {
			kftIn.setSuspectedFraudInfo(HofConst.FRAUD_SP);
		}
		
		kftIn.setMessageTrackingNumber(tlgTrceNo); /* 전문추적 관리번호 : 전문에만 쓰임*/
		kftIn.setTransactionIdNumber(trUnqNo); /* 거래고유번호 : 원장에 입력됨*/
		kftIn.setWithdrawer(whdrwl);
		kftIn.setTraxOccurredDate(LocalDate.now());
		kftIn.setMessageSendTime(LocalTime.now());
		kftIn.setBeneficiaryName(in.getBeneficiaryName());
		kftIn.setFiller(null);
		
		String priority = frwContext.getTlgHdr().get(WMQConstants.JMS_PRIORITY);
		if (StringUtils.isEmpty(priority)) {
			priority = hofCom.getPriorityCheck(in.getWithdrawalAccountNumber(),
					new BigDecimal(in.getBeneficiaryAmount()));
		}
		
		// KFTC QUEUE에 송신 + 우선순위 설정
		frwTemplate.send(FrwDestination.KFT_HOF, kftIn,
				Map.of(WMQConstants.JMS_PRIORITY, priority));
		
	}

	private void _createHoldTransaction(LvbHof0200400000 in, String holdRsnCd) {
		
		ComTrHoldL retTrHoldL = new ComTrHoldL();
		
		// 2025.03.18 대기건 중복방지
		try {
			HofLrgAmtSplitM lrgM = _getHofLrgAmtSplitM(in);
			
			ComTrHoldLIn comTrHoldLIn = new ComTrHoldLIn();
			comTrHoldLIn.setHostNo(in.getMsgNo());
			comTrHoldLIn.setHoldRsnCd(holdRsnCd);
			if (lrgM != null) {
				comTrHoldLIn.setSelTrUnqNo(in.getTransactionIdNumber());
			}
			retTrHoldL = bizCom.setComHoldStatusDone(comTrHoldLIn);
			log.debug("#########[UPDATE] 중복방지 COM_TR_HOLD 테이블 LOCK 해제, hostNo : {}", in.getMsgNo());
			
		} catch (Exception e) {
			log.error("_createHoldTransaction INNER COMMIT EXCEPTION CATCH", e);
		}
		
		String priority = frwContext.getTlgHdr().get(WMQConstants.JMS_PRIORITY);
		if (StringUtils.isEmpty(priority)) {
			priority = hofCom.getPriorityCheck(in.getWithdrawalAccountNumber(),
					new BigDecimal(in.getBeneficiaryAmount()));
		}

		boolean isCreateHold = true;
		if(retTrHoldL != null && holdRsnCd.equals(retTrHoldL.getHoldRsnCd())) isCreateHold = false;
		
		if(isCreateHold) {
			ComTrHoldL holdIn = new ComTrHoldL();
			holdIn.setBizDvsnCd(BizDvsnCdEnum.HOF.getValue());
			holdIn.setOutinDvsnCd(HofConst.OUTIN_DVSN_CD_01);
			holdIn.setTlgKndDvsnCd(in.getMessageType());
			holdIn.setTlgTrDvsnCd(in.getMessageCode());
			holdIn.setRcvBnkCd(in.getBeneficiaryBankCode());
			holdIn.setRcvAcctNo(in.getBeneficiaryAccountNumber());
			holdIn.setSndrRealNm(in.getSenderName());
			holdIn.setTrAmt(new BigDecimal(in.getBeneficiaryAmount()));
			holdIn.setTlgCtt(frwContext.getTlgCtt());
			holdIn.setHoldRsnCd(holdRsnCd);
			holdIn.setTrPrord(new BigDecimal(priority));
			holdIn.setHostNo(in.getMsgNo());
			holdIn.setWhdrwlAcctNo(in.getWithdrawalAccountNumber());
			holdIn.setTrUnqNo(in.getTransactionIdNumber());
			holdIn.setWhdrwlBnkCd(in.getRequestBank());
			holdIn.setWhdrwlNm(in.getSenderName());
			
			bizCom.createHoldTransaction(frwContext, holdIn);
		}
	}
	
	/**
	 * 출금인정보 재설정
	 * @param in
	 * @return
	 */
	private String _getSenderInfo(LvbHof0200400000 in) {
		
		return StringUtils.isNotEmpty(in.getDetailInformation())
		? in.getDetailInformation()
		: FundTypeEnum.PAYROLL.getCode().equals(in.getFundType())
				? HofConst.PAYROLL.concat(in.getSenderName())
				: in.getSenderName();
	}
	
	/**
	 * HOF_TR_L 원장 등록 공통 메소드
	 * 
	 * @param in
	 * @param isLargAmtTr
	 * @param orgnAmt
	 * @param trUnqId
	 * @param tlgTrceNo
	 * @param whdrwl
	 */
	private void _insertHofTransaction(	LvbHof0200400000 in,
										boolean isLargAmtTr, 
										BigDecimal orgnAmt, 
										String trUnqId, 
										String tlgTrceNo,
										String whdrwl) {
		
		// HOF_TR_L 원장에 입력할 거래정보
		HofTrL insertIn = new HofTrL();

		insertIn.setFnncFrdInfoCd(hbkCom.checkSpAcctYn(in.getWithdrawalAccountNumber()) ? HofConst.FRAUD_SP : null);
		insertIn.setOutinDvsnCd(HofConst.OUTIN_DVSN_CD_01);
		insertIn.setTlgKndDvsnCd(in.getMessageType());
		insertIn.setTlgTrDvsnCd(in.getMessageCode());
		insertIn.setTrStsCd(TrStsCdEnum.REQUEST.getTrStsCd());
		insertIn.setOpnBnkCd(in.getBeneficiaryBankCode());
		if(isLargAmtTr) {
			insertIn.setLrgAmtSplitTrYn(ComConst.Y);
			insertIn.setSplitOrgnTrTotAmt(orgnAmt);
		}
		insertIn.setTotTrAmt(new BigDecimal(in.getBeneficiaryAmount()));
		insertIn.setWhdrwlAcctNo(in.getWithdrawalAccountNumber());
		insertIn.setHostNo(in.getMsgNo());    //당발에서는 hostNo입력
		insertIn.setHofTlgTrceNo(tlgTrceNo);  //전문추적번호 채번 입력
		insertIn.setTrUnqNo(trUnqId);
		insertIn.setStrtChnlDvsnCd(in.getChannelType());
		insertIn.setTrmnlDvsnCd(TrmnlDvsnCdEnum.LVB.getCode());
		insertIn.setRqerNm(whdrwl);
		insertIn.setRqerNm2(in.getSenderName());
		insertIn.setSndrRealNm(in.getSenderName());
		// 의심계좌인 경우 정보셋팅
		if (hbkCom.checkSpAcctYn(in.getWithdrawalAccountNumber())) {
			insertIn.setFnncFrdInfoCd(HofConst.FRAUD_SP);
		}
		// HOF_TR_L 원장에 거래내역 신규
		hofCom.insertHofTransaction(insertIn);
		
	}
	
	/**
	 * 예외
	 */
	@Override
	public void handleError(LvbHof0200400000 in, Throwable t) {
		String respCd;
		boolean isLargeAmtSplitTr = false;		// 거액거래여부 판단

		if (t instanceof InternalResponseException e) {
			respCd = e.getRespCd();
		} else {
			respCd = HofRespCdEnum.RESP_CD_413.getCode();
			log.info(t.getStackTrace().toString());
		}
		
		HofTrL insertIn = new HofTrL();
		// 거액분할거래가 내부에러일 수 있으므로 전문검색
		HofLrgAmtSplitM lrgM = _getHofLrgAmtSplitM(in);
		if (lrgM != null) {
			isLargeAmtSplitTr = true;
			// HOF_LRG_AMT_SPLIT_L 거액분할거래 에러update
			HofLrgAmtSplitTrL lrgTrL = new HofLrgAmtSplitTrL();
			lrgTrL.setTrDt(lrgM.getTrDt());
			lrgTrL.setTrUnqNo(in.getTransactionIdNumber());
			lrgTrL.setTrStsCd(TrStsCdEnum.ERROR.getTrStsCd());
			lrgTrL.setHostNo(lrgM.getHostNo());
			hofCom.updateLrgAmtSplitTransaction(lrgTrL);

			// 재조회하여 M상태 update
			lrgM = _getHofLrgAmtSplitM(in);
			
			insertIn.setLrgAmtSplitTrYn(ComConst.Y);  //거액거래여부
		}
		
		// 오류내용 응답 전송 
		LvbHof0210400000 respVo = VOUtils.toVo(frwContext.getOrgnTlgCtt(), LvbHof0210400000.class);
		respVo.setMessageType(HofConst.TLG_KND_DVSN_NO_0210);
		respVo.setSystemSendReceiveTime(LocalDateTime.now());
		respVo.setResponseCode(respCd);
		respVo.setMsgType(HofConst.KCGLVB);
		if (StringUtils.isNotEmpty(in.getTransactionIdNumber())) {
			respVo.setTransactionIdNumber(in.getTransactionIdNumber());
		}
		log.debug("#########거액분할처리 에러 여부 : [{}]=={}", isLargeAmtSplitTr, lrgM);
		if(isLargeAmtSplitTr && TrStsCdEnum.ERROR.getTrStsCd().equals(lrgM.getTrStsCd())) {
			respVo.setBeneficiaryAmount(lrgM.getSplitOrgnTrTotAmt().longValue());
		}
		frwTemplate.send(FrwDestination.LVB_HOF, respVo);

		insertIn.setTlgKndDvsnCd(HofConst.TLG_KND_DVSN_NO_0210);
		insertIn.setOutinDvsnCd(HofConst.OUTIN_DVSN_CD_01);
		insertIn.setTlgTrDvsnCd(HofConst.TR_DVSN_400000);
		insertIn.setHndlBnkCd(ComConst.JPMC_BANK_CD);
		insertIn.setHndlBrnchCd(ComConst.JPMC_BRNCH_CD);
		insertIn.setOpnBnkCd(in.getBeneficiaryBankCode());
		insertIn.setTrStsCd(TrStsCdEnum.ERROR.getTrStsCd());
		insertIn.setRespCd(respCd);
		insertIn.setHostNo(in.getMsgNo());    //당발에서는 hostNo입력
		insertIn.setTrmnlDvsnCd(TrmnlDvsnCdEnum.LVB.getCode());
		insertIn.setStrtChnlDvsnCd(in.getChannelType());
		insertIn.setTrUnqNo(in.getTransactionIdNumber());
		insertIn.setTotTrAmt(new BigDecimal(in.getBeneficiaryAmount()));
		insertIn.setRqerNm(_getSenderInfo(in));
		insertIn.setRqerNm2(in.getSenderName());
		insertIn.setSndrRealNm(in.getSenderName());
		
		if (HofConst.TR_DVSN_300000.equals(in.getMessageCode())) { //수취계좌조회오류는 상태코드 별도
			// HOF_TR_L 원장에 입력할 거래정보
			insertIn.setTrStsCd(TrStsCdEnum.BENE_INQ_ERROR.getTrStsCd());
			insertIn.setTrUnqNo(in.getTransactionIdNumber());
			insertIn.setHofTlgTrceNo(in.getTransactionIdNumber().substring(5));
		} else if (HofRespCdEnum.RESP_CD_983.getCode().equals(respCd)) { // 미처리거래
			insertIn.setTrStsCd(TrStsCdEnum.PRE_REQUEST.getTrStsCd());
			insertIn.setRespCd(null);
			insertIn.setHostRespCd(respCd);
		} else if (respCd.startsWith(ComConst.CHAR_0)) {
			insertIn.setTlgKndDvsnCd(HofConst.TLG_KND_DVSN_NO_9200);
			try {
				Map<String, Object> errorValList = new Hashtable<>();
				// 에러 메시지 문자열 생성
				String errorMessage = String.format("Response Code : %s , Host Message Number: %s",
						respCd, in.getMsgNo());
				errorValList.put("errTlgId", HofConst.FORMAT_ERROR_TITLE);
				errorValList.put("errCtt", errorMessage);
				comPshMsgBean.sendAlarm(8, errorValList.get("errTlgId").toString(), errorValList,
						null, true);

				/**
				 * 2025-02-03 추가. 계좌번호에 대한 즉시 알람인 경우 처리.
				 * 2025-04-25 아래 발송하고 있으므로 주석 처리 (hof_tr_l 저장 전 호출 하고 있음)
				 */
				/*
				if(_checkMailSendYn(in, isLargeAmtSplitTr)) {
					ComAcctAlarmIn comAcctAlarmIn = new ComAcctAlarmIn();
					comAcctAlarmIn.setTranDt		(	in.getTransactionDate().format(DateFormat.YYYYMMDD.getFormatter()));
					comAcctAlarmIn.setTrUnqNo		(	in.getTransactionIdNumber()		);
					comAcctAlarmIn.setAcctNo		(	in.getWithdrawalAccountNumber()	);
					comAcctAlarmIn.setAlarmTrDvsnCd	(	ComConst.CHAR_02				);	// 불능(오류)
					hofCom.sendDrctAcctAlarm(comAcctAlarmIn);
				}
				*/
			} catch (Exception e2) {
				log.error("Message Request Fail", e2);
			}
		}
		// HOF_TR_L 테이블에 오류난 거래내역 입력
		hofCom.insertHofTransaction(insertIn);
		
		// 2024.08.08 추가 해당 거래가 hold테이블에 있는 경우 release로 변경
		try {
			ComTrHoldLIn comTrHoldLIn = new ComTrHoldLIn();
			comTrHoldLIn.setHostNo(in.getMsgNo());
			if (lrgM != null) {		// 거액일때만 set
				comTrHoldLIn.setSelTrUnqNo(in.getTransactionIdNumber());
			}
			bizCom.setComHoldStatusDone(comTrHoldLIn);
			log.debug("#########[UPDATE] 중복방지 COM_TR_HOLD 테이블 LOCK 해제, hostNo : {}", in.getMsgNo());
			
		} catch (Exception e) {
			log.error("handleError INNER COMMIT EXCEPTION CATCH", e);
		}
		
		if(_checkMailSendYn(in, isLargeAmtSplitTr)) {
			/**
			 * 2025.03.20 수취조회 불능 시 알람 전송
			 */
			try {
				ComAcctAlarmIn comAcctAlarmIn = new ComAcctAlarmIn();
				comAcctAlarmIn.setTranDt		(	in.getTransactionDate().format(DateFormat.YYYYMMDD.getFormatter()));
				comAcctAlarmIn.setTrUnqNo		(	in.getTransactionIdNumber()		);
				comAcctAlarmIn.setAcctNo		(	in.getWithdrawalAccountNumber()	);
				comAcctAlarmIn.setAlarmTrDvsnCd	(	ComConst.CHAR_02				);
				comAcctAlarmIn.setDfltSendFlag	(	ComConst.Y						);
				hofCom.sendDrctAcctAlarm(comAcctAlarmIn);
			} catch (Exception e) {
				log.error("Message Request Fail", e);
			}
		}

		return;
	}
	
	private boolean _checkMailSendYn(LvbHof0200400000 in, boolean isLargeAmtSplit) {
		
		boolean sendFlag = false;
		// 거액분할
		if(isLargeAmtSplit) {
			HofLrgAmtSplitTrL hofLrgAmtSplitTrL = new HofLrgAmtSplitTrL();
			hofLrgAmtSplitTrL.setTrDt(in.getTransactionDate().format(DateFormat.YYYYMMDD.getFormatter()));
			hofLrgAmtSplitTrL.setHostNo(in.getMsgNo());
			
			HofLrgAmtSplitTrLSumOut splitTrL = hofCom.checkHofLrgAmtSplitLastTr(true, hofLrgAmtSplitTrL);
			
			if(null != splitTrL) {
				sendFlag = splitTrL.isLastTr();
			}
		} else {
			sendFlag = true;
		}
		
		log.debug("#########_checkMailSendYn######### hostNo : [{}]-[{}]", in.getMsgNo(), sendFlag);
		
		return sendFlag;
	}
}

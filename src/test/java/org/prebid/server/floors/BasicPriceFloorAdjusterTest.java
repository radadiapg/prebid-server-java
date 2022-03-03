package org.prebid.server.floors;

import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidadjustmentfactors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;

public class BasicPriceFloorAdjusterTest extends VertxTest {

    private static final String RUBICON = "rubicon";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private BasicPriceFloorAdjuster basicPriceFloorAdjuster;

    @Before
    public void setUp() {
        basicPriceFloorAdjuster = new BasicPriceFloorAdjuster();
    }

    @Test
    public void adjustForImpShouldApplyFactorToBidFloorIfPresent() {
        // when
        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster
                .adjustForImp(givenImp(identity()), RUBICON, givenBidRequest(identity()));

        // then
        assertThat(adjustedBidFloor).isEqualTo(BigDecimal.valueOf(11.7647D));
    }

    @Test
    public void adjustForImpShouldChooseAdjustmentFactorFromAdjustments() {
        // given

        final ExtRequestBidadjustmentfactors givenFactors = ExtRequestBidadjustmentfactors.builder()
                .mediatypes(givenMediaTypes(Collections.emptyMap()))
                .build();
        givenFactors.addFactor(RUBICON, BigDecimal.valueOf(0.6D));
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .bidadjustmentfactors(givenFactors).build())));
        final Imp imp = givenImp(impBuilder ->
                impBuilder);

        // when
        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster.adjustForImp(imp, RUBICON, bidRequest);

        // then
        assertThat(adjustedBidFloor).isEqualTo(BigDecimal.valueOf(16.6667D));
    }

    @Test
    public void adjustForImpShouldNotChooseAdjustmentFactorIfGreaterThanOne() {
        // given

        final ExtRequestBidadjustmentfactors givenFactors = ExtRequestBidadjustmentfactors.builder()
                .mediatypes(givenMediaTypes(Map.of(
                        ImpMediaType.video,
                        Map.of(RUBICON, BigDecimal.valueOf(1.8D)))))
                .build();
        givenFactors.addFactor(RUBICON, BigDecimal.valueOf(1.6D));
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .bidadjustmentfactors(givenFactors).build())));
        final Imp imp = givenImp(impBuilder ->
                impBuilder);

        // when
        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster.adjustForImp(imp, RUBICON, bidRequest);

        // then
        assertThat(adjustedBidFloor).isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void adjustForImpShouldApplyNoAdjustmentsIfBidAdjustmentsFactorIsNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder().bidadjustmentfactors(null).build())));
        final Imp imp = givenImp(identity());

        // when
        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster.adjustForImp(imp, RUBICON, bidRequest);

        // then
        assertThat(adjustedBidFloor).isEqualTo(imp.getBidfloor());
    }

    @Test
    public void adjustForImpShouldReturnNullIfImpBidFloorIsNotPresent() {
        // given
        final Imp imp = givenImp(impBuilder -> impBuilder.bidfloor(null));

        // when
        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster
                .adjustForImp(imp, RUBICON, givenBidRequest(identity()));

        // then
        assertThat(adjustedBidFloor).isNull();
    }

    @Test
    public void adjustForImpShouldReturnBidFloorNotFactoredByOtherBidder() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .bidadjustmentfactors(ExtRequestBidadjustmentfactors.builder()
                                .mediatypes(givenMediaTypes(Map.of(
                                        ImpMediaType.video,
                                        Map.of("bidder", BigDecimal.valueOf(0.8D)))))
                                .build())
                        .build())));

        // when
        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster
                .adjustForImp(givenImp(identity()), RUBICON, bidRequest);

        // then
        assertThat(adjustedBidFloor).isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void adjustForImpShouldReturnFactoredOfOneIfExtBidAdjustmentsFactorMediaTypesIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .bidadjustmentfactors(ExtRequestBidadjustmentfactors.builder()
                                .mediatypes(null)
                                .build())
                        .build())));

        // when
        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster
                .adjustForImp(givenImp(identity()), RUBICON, bidRequest);

        // then
        assertThat(adjustedBidFloor).isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void adjustForImpShouldReturnFactorOfOneIfNoMediaTypeInImpression() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final Imp imp = givenImp(impBuilder -> impBuilder.video(null));

        // when
        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster
                .adjustForImp(imp, RUBICON, bidRequest);

        // then
        assertThat(adjustedBidFloor).isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void adjustForImpShouldSkipMediaTypeIfNoMediaTypesOfImpFound() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .bidadjustmentfactors(ExtRequestBidadjustmentfactors.builder()
                                .mediatypes(givenMediaTypes(Map.of(
                                        ImpMediaType.video_outstream,
                                        Map.of(RUBICON, BigDecimal.valueOf(0.8D)))))
                                .build())
                        .build())));

        // when
        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster
                .adjustForImp(givenImp(identity()), RUBICON, bidRequest);

        // then
        assertThat(adjustedBidFloor).isEqualTo(BigDecimal.TEN);
    }

    @Test
    public void adjustForImpShouldChooseMinimalFactorFromSeveralAvailable() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .bidadjustmentfactors(ExtRequestBidadjustmentfactors.builder()
                                .mediatypes(givenMediaTypes(Map.of(
                                        ImpMediaType.video_outstream,
                                        Map.of(RUBICON, BigDecimal.valueOf(0.8D), "bidder", BigDecimal.valueOf(0.8D)),
                                        ImpMediaType.audio,
                                        Map.of(RUBICON, BigDecimal.valueOf(0.75D), "bidder", BigDecimal.valueOf(0.6D)),
                                        ImpMediaType.xNative,
                                        Map.of(RUBICON, BigDecimal.valueOf(0.6D), "bidder", BigDecimal.valueOf(0.7D)))))
                                .build())
                        .build())));
        final Imp imp = givenImp(impBuilder ->
                impBuilder
                        .banner(Banner.builder().build())
                        .audio(Audio.builder().build())
                        .xNative(Native.builder().build())
                        .video(Video.builder().placement(0).build()));

        // when
        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster.adjustForImp(imp, RUBICON, bidRequest);

        // then
        assertThat(adjustedBidFloor).isEqualTo(BigDecimal.valueOf(16.6667D));
    }

    @Test
    public void adjustForImpShouldChooseAdjustmentFactorIfLowerThenMediaType() {
        // given

        final ExtRequestBidadjustmentfactors givenFactors = ExtRequestBidadjustmentfactors.builder()
                .mediatypes(givenMediaTypes(Map.of(
                        ImpMediaType.video,
                        Map.of(RUBICON, BigDecimal.valueOf(0.7D), "bidder", BigDecimal.valueOf(0.5D)))))
                .build();
        givenFactors.addFactor(RUBICON, BigDecimal.valueOf(0.6D));
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .bidadjustmentfactors(givenFactors).build())));
        final Imp imp = givenImp(impBuilder ->
                impBuilder);

        // when
        final BigDecimal adjustedBidFloor = basicPriceFloorAdjuster.adjustForImp(imp, RUBICON, bidRequest);

        // then
        assertThat(adjustedBidFloor).isEqualTo(BigDecimal.valueOf(16.6667D));
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> requestCustomizer) {
        return requestCustomizer.apply(
                        BidRequest.builder()
                                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                        .bidadjustmentfactors(ExtRequestBidadjustmentfactors.builder()
                                                .mediatypes(givenMediaTypes(Map.of(
                                                        ImpMediaType.video,
                                                        Map.of(RUBICON, BigDecimal.valueOf(0.85D)))))
                                                .build())
                                        .build())))
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("impId")
                        .bidfloor(BigDecimal.TEN)
                        .video(Video.builder().placement(1).build())
                        .bidfloorcur("USD")
                        .ext(jacksonMapper.mapper().createObjectNode()))
                .build();
    }

    private static EnumMap<ImpMediaType, Map<String, BigDecimal>> givenMediaTypes(
            Map<ImpMediaType, Map<String, BigDecimal>> values) {
        final EnumMap<ImpMediaType, Map<String, BigDecimal>> mediaTypes = new EnumMap<>(ImpMediaType.class);
        mediaTypes.putAll(values);

        return mediaTypes;
    }
}
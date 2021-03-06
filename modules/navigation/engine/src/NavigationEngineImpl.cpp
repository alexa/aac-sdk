/*
 * Copyright 2017-2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

#include <sstream>
#include <string.h>
#include "AACE/Engine/Navigation/NavigationEngineImpl.h"
#include "AACE/Engine/Core/EngineMacros.h"
#include <AACE/Engine/Utils/Metrics/Metrics.h>

#include <rapidjson/document.h>
#include <rapidjson/error/en.h>

namespace aace {
namespace engine {
namespace navigation {

using namespace aace::engine::utils::metrics;

// String to identify log entries originating from this file.
static const std::string TAG("aace.navigation.NavigationEngineImpl");

/// Program Name for Metrics
static const std::string METRIC_PROGRAM_NAME_SUFFIX = "NavigationEngineImpl";

/// Counter Metrics for Navigation Platform APIs
static const std::string METRIC_NAVIGATION_SHOW_PREVIOUS_WAYPOINTS = "ShowPreviousWaypoints";
static const std::string METRIC_NAVIGATION_NAVIGATE_TO_PREVIOUS_WAYPOINT = "NavigateToPreviousWaypoint";
static const std::string METRIC_NAVIGATION_SHOW_ALTERNATIVE_ROUTES = "ShowAlternativeRoutes";
static const std::string METRIC_NAVIGATION_CONTROL_DISPLAY = "ControlDisplay";
static const std::string METRIC_NAVIGATION_CANCEL_NAVIGATION = "CancelNavigation";
static const std::string METRIC_NAVIGATION_GET_NAVIGATION_STATE = "GetNavigationState";
static const std::string METRIC_NAVIGATION_START_NAVIGATION = "StartNavigation";
static const std::string METRIC_NAVIGATION_ANNOUNCE_MANEUVER = "AnnounceManeuver";
static const std::string METRIC_NAVIGATION_ANNOUNCE_ROADREGULATION = "AnnounceRoadRegulation";
static const std::string METRIC_NAVIGATION_NAVIGATION_EVENT = "NavigationEvent";
static const std::string METRIC_NAVIGATION_NAVIGATION_ERROR = "NavigationError";
static const std::string METRIC_NAVIGATION_SHOW_ALTERNATIVE_ROUTES_SUCCEEDED = "ShowAlternativeRoutesSucceeded";

NavigationEngineImpl::NavigationEngineImpl(
    std::shared_ptr<aace::navigation::Navigation> navigationPlatformInterface,
    const std::string& navigationProviderName) :
        alexaClientSDK::avsCommon::utils::RequiresShutdown(TAG),
        m_navigationPlatformInterface(navigationPlatformInterface),
        m_navigationProviderName{navigationProviderName} {
}

bool NavigationEngineImpl::initialize(
    std::shared_ptr<alexaClientSDK::avsCommon::sdkInterfaces::endpoints::EndpointCapabilitiesRegistrarInterface>
        capabilitiesRegistrar,
    std::shared_ptr<alexaClientSDK::avsCommon::sdkInterfaces::ExceptionEncounteredSenderInterface> exceptionSender,
    std::shared_ptr<alexaClientSDK::avsCommon::sdkInterfaces::MessageSenderInterface> messageSender,
    std::shared_ptr<alexaClientSDK::avsCommon::sdkInterfaces::ContextManagerInterface> contextManager) {
    try {
        m_navigationCapabilityAgent = NavigationCapabilityAgent::create(
            shared_from_this(), exceptionSender, messageSender, contextManager, m_navigationProviderName);
        ThrowIfNull(m_navigationCapabilityAgent, "couldNotCreateNavigationCapabilityAgent");

        m_navigationAssistanceCapabilityAgent = navigationassistance::NavigationAssistanceCapabilityAgent::create(
            shared_from_this(), exceptionSender, messageSender, contextManager);
        ThrowIfNull(m_navigationAssistanceCapabilityAgent, "couldNotCreateNavigationAssistanceCapabilityAgent");

        m_displayManagerCapabilityAgent = displaymanager::DisplayManagerCapabilityAgent::create(
            shared_from_this(), exceptionSender, messageSender, contextManager);
        ThrowIfNull(m_displayManagerCapabilityAgent, "couldNotCreateDisplayManagerCapabilityAgent");

        // register capability with the default endpoint
        capabilitiesRegistrar->withCapability(m_navigationCapabilityAgent, m_navigationCapabilityAgent);
        capabilitiesRegistrar->withCapability(m_displayManagerCapabilityAgent, m_displayManagerCapabilityAgent);
        capabilitiesRegistrar->withCapability(
            m_navigationAssistanceCapabilityAgent, m_navigationAssistanceCapabilityAgent);

        return true;
    } catch (std::exception& ex) {
        AACE_ERROR(LX(TAG, "initialize").d("reason", ex.what()));
        return false;
    }
}

std::shared_ptr<NavigationEngineImpl> NavigationEngineImpl::create(
    std::shared_ptr<aace::navigation::Navigation> navigationPlatformInterface,
    std::shared_ptr<alexaClientSDK::avsCommon::sdkInterfaces::endpoints::EndpointCapabilitiesRegistrarInterface>
        capabilitiesRegistrar,
    std::shared_ptr<alexaClientSDK::avsCommon::sdkInterfaces::ExceptionEncounteredSenderInterface> exceptionSender,
    std::shared_ptr<alexaClientSDK::avsCommon::sdkInterfaces::MessageSenderInterface> messageSender,
    std::shared_ptr<alexaClientSDK::avsCommon::sdkInterfaces::ContextManagerInterface> contextManager,
    const std::string& navigationProviderName) {
    try {
        ThrowIfNull(navigationPlatformInterface, "nullNavigationPlatformInterface");
        ThrowIfNull(capabilitiesRegistrar, "nullCapabilitiesRegistrar");
        ThrowIfNull(exceptionSender, "nullPlatformInterface");
        ThrowIfNull(contextManager, "nullNavigationContextManager");

        std::shared_ptr<NavigationEngineImpl> navigationEngineImpl = std::shared_ptr<NavigationEngineImpl>(
            new NavigationEngineImpl(navigationPlatformInterface, navigationProviderName));

        ThrowIfNot(
            navigationEngineImpl->initialize(capabilitiesRegistrar, exceptionSender, messageSender, contextManager),
            "initializeNavigationEngineImplFailed");

        // set the platform engine interface reference
        navigationPlatformInterface->setEngineInterface(navigationEngineImpl);

        return navigationEngineImpl;
    } catch (std::exception& ex) {
        AACE_ERROR(LX(TAG, "create").d("reason", ex.what()));
        return nullptr;
    }
}

void NavigationEngineImpl::doShutdown() {
    if (m_navigationCapabilityAgent != nullptr) {
        m_navigationCapabilityAgent->shutdown();
        m_navigationCapabilityAgent.reset();
    }

    if (m_displayManagerCapabilityAgent != nullptr) {
        m_displayManagerCapabilityAgent->shutdown();
        m_displayManagerCapabilityAgent.reset();
    }

    if (m_navigationAssistanceCapabilityAgent != nullptr) {
        m_navigationAssistanceCapabilityAgent->shutdown();
        m_navigationAssistanceCapabilityAgent.reset();
    }

    if (m_navigationPlatformInterface != nullptr) {
        m_navigationPlatformInterface->setEngineInterface(nullptr);
    }
}

void NavigationEngineImpl::showPreviousWaypoints() {
    emitCounterMetrics(
        METRIC_PROGRAM_NAME_SUFFIX, "showPreviousWaypoints", {METRIC_NAVIGATION_SHOW_PREVIOUS_WAYPOINTS});
    m_navigationPlatformInterface->showPreviousWaypoints();
}

void NavigationEngineImpl::navigateToPreviousWaypoint() {
    emitCounterMetrics(
        METRIC_PROGRAM_NAME_SUFFIX, "navigateToPreviousWaypoint", {METRIC_NAVIGATION_NAVIGATE_TO_PREVIOUS_WAYPOINT});
    m_navigationPlatformInterface->navigateToPreviousWaypoint();
}

void NavigationEngineImpl::showAlternativeRoutes(aace::navigation::Navigation::AlternateRouteType alternateRouteType) {
    std::stringstream ss;
    ss << alternateRouteType;
    emitCounterMetrics(
        METRIC_PROGRAM_NAME_SUFFIX, "showAlternativeRoutes", {METRIC_NAVIGATION_SHOW_ALTERNATIVE_ROUTES, ss.str()});
    m_navigationPlatformInterface->showAlternativeRoutes(alternateRouteType);
}

void NavigationEngineImpl::controlDisplay(aace::navigation::Navigation::ControlDisplay controlDisplay) {
    std::stringstream ss;
    ss << controlDisplay;
    emitCounterMetrics(METRIC_PROGRAM_NAME_SUFFIX, "controlDisplay", {METRIC_NAVIGATION_CONTROL_DISPLAY, ss.str()});
    m_navigationPlatformInterface->controlDisplay(controlDisplay);
}

void NavigationEngineImpl::startNavigation(const std::string& payload) {
    emitCounterMetrics(METRIC_PROGRAM_NAME_SUFFIX, "startNavigation", {METRIC_NAVIGATION_START_NAVIGATION});
    m_navigationPlatformInterface->startNavigation(payload);
}

void NavigationEngineImpl::announceManeuver(const std::string& payload) {
    emitCounterMetrics(METRIC_PROGRAM_NAME_SUFFIX, "announceManeuver", {METRIC_NAVIGATION_ANNOUNCE_MANEUVER});
    m_navigationPlatformInterface->announceManeuver(payload);
}

void NavigationEngineImpl::announceRoadRegulation(aace::navigation::Navigation::RoadRegulation roadRegulation) {
    std::stringstream ss;
    ss << roadRegulation;
    emitCounterMetrics(
        METRIC_PROGRAM_NAME_SUFFIX, "announceRoadRegulation", {METRIC_NAVIGATION_ANNOUNCE_ROADREGULATION, ss.str()});
    m_navigationPlatformInterface->announceRoadRegulation(roadRegulation);
}

void NavigationEngineImpl::cancelNavigation() {
    emitCounterMetrics(METRIC_PROGRAM_NAME_SUFFIX, "cancelNavigation", {METRIC_NAVIGATION_CANCEL_NAVIGATION});
    m_navigationPlatformInterface->cancelNavigation();
}

std::string NavigationEngineImpl::getNavigationState() {
    emitCounterMetrics(METRIC_PROGRAM_NAME_SUFFIX, "getNavigationState", {METRIC_NAVIGATION_GET_NAVIGATION_STATE});
    return m_navigationPlatformInterface->getNavigationState();
}

void NavigationEngineImpl::onNavigationEvent(EventName event) {
    std::stringstream ss;
    ss << event;
    emitCounterMetrics(METRIC_PROGRAM_NAME_SUFFIX, "onNavigationEvent", {METRIC_NAVIGATION_NAVIGATION_EVENT, ss.str()});
    switch (event) {
        case aace::navigation::NavigationEngineInterface::EventName::NAVIGATION_STARTED:
        case aace::navigation::NavigationEngineInterface::EventName::PREVIOUS_WAYPOINTS_SHOWN:
        case aace::navigation::NavigationEngineInterface::EventName::PREVIOUS_NAVIGATION_STARTED:
            m_navigationCapabilityAgent->navigationEvent(event);
            break;
        case aace::navigation::NavigationEngineInterface::EventName::ROUTE_OVERVIEW_SHOWN:
        case aace::navigation::NavigationEngineInterface::EventName::DIRECTIONS_LIST_SHOWN:
        case aace::navigation::NavigationEngineInterface::EventName::ZOOMED_IN:
        case aace::navigation::NavigationEngineInterface::EventName::ZOOMED_OUT:
        case aace::navigation::NavigationEngineInterface::EventName::MAP_CENTERED:
        case aace::navigation::NavigationEngineInterface::EventName::ORIENTED_NORTH:
        case aace::navigation::NavigationEngineInterface::EventName::SCROLLED_NORTH:
        case aace::navigation::NavigationEngineInterface::EventName::SCROLLED_UP:
        case aace::navigation::NavigationEngineInterface::EventName::SCROLLED_EAST:
        case aace::navigation::NavigationEngineInterface::EventName::SCROLLED_RIGHT:
        case aace::navigation::NavigationEngineInterface::EventName::SCROLLED_SOUTH:
        case aace::navigation::NavigationEngineInterface::EventName::SCROLLED_DOWN:
        case aace::navigation::NavigationEngineInterface::EventName::SCROLLED_WEST:
        case aace::navigation::NavigationEngineInterface::EventName::SCROLLED_LEFT:
        case aace::navigation::NavigationEngineInterface::EventName::ROUTE_GUIDANCE_MUTED:
        case aace::navigation::NavigationEngineInterface::EventName::ROUTE_GUIDANCE_UNMUTED:
            m_displayManagerCapabilityAgent->navigationEvent(event);
            break;
        case aace::navigation::NavigationEngineInterface::EventName::TURN_GUIDANCE_ANNOUNCED:
        case aace::navigation::NavigationEngineInterface::EventName::EXIT_GUIDANCE_ANNOUNCED:
        case aace::navigation::NavigationEngineInterface::EventName::ENTER_GUIDANCE_ANNOUNCED:
        case aace::navigation::NavigationEngineInterface::EventName::MERGE_GUIDANCE_ANNOUNCED:
        case aace::navigation::NavigationEngineInterface::EventName::LANE_GUIDANCE_ANNOUNCED:
        case aace::navigation::NavigationEngineInterface::EventName::SPEED_LIMIT_REGULATION_ANNOUNCED:
        case aace::navigation::NavigationEngineInterface::EventName::CARPOOL_RULES_REGULATION_ANNOUNCED:
            m_navigationAssistanceCapabilityAgent->navigationEvent(event);
            break;
        default:
            AACE_WARN(LX(TAG, "onNavigationEvent").d("reason", "Invalid EventName"));
            break;
    }
}

void NavigationEngineImpl::onNavigationError(
    aace::navigation::NavigationEngineInterface::ErrorType type,
    aace::navigation::NavigationEngineInterface::ErrorCode code,
    const std::string& description) {
    std::stringstream errorType;
    std::stringstream errorCode;
    errorType << type;
    errorCode << code;
    emitCounterMetrics(
        METRIC_PROGRAM_NAME_SUFFIX,
        "onNavigationError",
        {METRIC_NAVIGATION_NAVIGATION_ERROR, errorType.str(), errorCode.str()});
    switch (type) {
        case aace::navigation::NavigationEngineInterface::ErrorType::NAVIGATION_START_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::SHOW_PREVIOUS_WAYPOINTS_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::PREVIOUS_NAVIGATION_START_FAILED:
            m_navigationCapabilityAgent->navigationError(type, code, description);
            break;
        case aace::navigation::NavigationEngineInterface::ErrorType::ROUTE_OVERVIEW_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::DIRECTIONS_LIST_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::ZOOM_IN_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::ZOOM_OUT_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::CENTER_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::ORIENT_NORTH_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::SCROLL_NORTH_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::SCROLL_UP_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::SCROLL_EAST_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::SCROLL_RIGHT_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::SCROLL_SOUTH_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::SCROLL_DOWN_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::SCROLL_WEST_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::SCROLL_LEFT_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::MUTED_ROUTE_GUIDANCE_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::UNMUTED_ROUTE_GUIDANCE_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::DEFAULT_ALTERNATE_ROUTES_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::SHORTER_TIME_ROUTES_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::SHORTER_DISTANCE_ROUTES_FAILED:
            m_displayManagerCapabilityAgent->navigationError(type, code, description);
            break;
        case aace::navigation::NavigationEngineInterface::ErrorType::TURN_GUIDANCE_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::EXIT_GUIDANCE_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::ENTER_GUIDANCE_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::MERGE_GUIDANCE_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::LANE_GUIDANCE_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::SPEED_LIMIT_REGULATION_FAILED:
        case aace::navigation::NavigationEngineInterface::ErrorType::CARPOOL_RULES_REGULATION_FAILED:
            m_navigationAssistanceCapabilityAgent->navigationError(type, code, description);
            break;
        default:
            AACE_WARN(LX(TAG, "onNavigationError").d("reason", "Invalid Navigation ErrorType"));
            break;
    }
}

void NavigationEngineImpl::onShowAlternativeRoutesSucceeded(const std::string& payload) {
    emitCounterMetrics(
        METRIC_PROGRAM_NAME_SUFFIX,
        "onShowAlternativeRoutesSucceeded",
        {METRIC_NAVIGATION_SHOW_ALTERNATIVE_ROUTES_SUCCEEDED});
    m_displayManagerCapabilityAgent->showAlternativeRoutesSucceeded(payload);
}

}  // namespace navigation
}  // namespace engine
}  // namespace aace

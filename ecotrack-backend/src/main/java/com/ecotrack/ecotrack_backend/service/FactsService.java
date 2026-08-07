package com.ecotrack.ecotrack_backend.service;

import com.ecotrack.ecotrack_backend.dto.FactResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

// Static, non-personalized sustainability trivia (Decision H). Deliberately
// not backed by a DB table or repository — this is a fixed reference list,
// not user data, so there's nothing to persist or query per-user.
@Service
public class FactsService {

    private static final List<String> FACTS = List.of(
            "A single mature tree can absorb up to 22 kg of CO2 per year.",
            "Producing 1 kg of beef generates roughly 27 kg of CO2-equivalent emissions.",
            "LED bulbs use up to 80% less energy than traditional incandescent bulbs.",
            "The average car emits about 4.6 metric tons of CO2 per year.",
            "Recycling one aluminum can saves enough energy to run a TV for three hours.",
            "Food waste accounts for roughly 8-10% of global greenhouse gas emissions.",
            "A dripping tap can waste over 11,000 litres of water a year.",
            "Public transport produces far fewer emissions per passenger than private cars.",
            "Composting food scraps can reduce methane emissions from landfills.",
            "Unplugging idle electronics can cut a household's energy use by up to 10%.",
            "Cotton farming uses an enormous amount of water — about 2,700 litres per t-shirt.",
            "Switching to a plant-based meal once a week can meaningfully cut your carbon footprint.",
            "Solar panel costs have dropped by over 80% in the last decade.",
            "The fashion industry is responsible for about 10% of global carbon emissions.",
            "Air-drying clothes instead of using a dryer can save significant household energy.",
            "Reducing food waste at home is one of the most effective individual climate actions.",
            "A single reusable water bottle can replace hundreds of single-use plastic bottles a year.",
            "Deforestation contributes to roughly 10% of global warming emissions.",
            "Home insulation upgrades can cut heating-related emissions substantially.",
            "Electric vehicles typically produce fewer lifetime emissions than petrol/diesel cars, even accounting for battery production.",
            "Ocean phytoplankton produce over half of the world's oxygen.",
            "Cycling short distances instead of driving can cut your personal transport emissions significantly.",
            "Food transported by air has a far larger carbon footprint than locally sourced food.",
            "Turning off lights in unused rooms is a small habit with a measurable cumulative impact.",
            "Wind power is now one of the cheapest sources of new electricity in many countries.",
            "A vegetarian diet can cut an individual's food-related carbon footprint by roughly half.",
            "Repairing or reusing electronics reduces the emissions tied to manufacturing new ones.",
            "Green roofs help lower building energy use by improving insulation.",
            "Reducing single-use plastic helps cut the emissions tied to plastic production.",
            "Carpooling regularly can meaningfully reduce per-person transport emissions.",
            "Rainwater harvesting can significantly reduce a household's treated-water usage.",
            "Energy-efficient appliances can cut household electricity use by 10-50% depending on the appliance."
    );

    public FactResponse getRandomFact() {
        int index = ThreadLocalRandom.current().nextInt(FACTS.size());
        return new FactResponse(FACTS.get(index));
    }
}